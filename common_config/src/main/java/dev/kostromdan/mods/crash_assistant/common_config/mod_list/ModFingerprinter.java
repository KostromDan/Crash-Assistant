package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A utility class to generate identification fingerprints for mod files.
 * This handles the specific hashing requirements for both:
 * 1. The "Normalized" MurmurHash2 used by CurseForge.
 * 2. The Standard SHA-1 used by Modrinth.
 */
public class ModFingerprinter {

    /**
     * A container class to hold the pair of calculated hashes.
     */
    public static class IdentificationResult {
        private final long curseForgeHash;
        private final String modrinthHash;

        public IdentificationResult(long curseForgeHash, String modrinthHash) {
            this.curseForgeHash = curseForgeHash;
            this.modrinthHash = modrinthHash;
        }

        /**
         * @return The normalized Murmur2 hash as a generic unsigned int (stored as long).
         */
        public long getCurseForgeHash() {
            return curseForgeHash;
        }

        /**
         * @return The standard SHA-1 hash as a hexadecimal string.
         */
        public String getModrinthHash() {
            return modrinthHash;
        }

        @Override
        public String toString() {
            return String.format("Result[CF=%d, MR=%s]", curseForgeHash, modrinthHash);
        }
    }

    /**
     * Computes the fingerprints for the specified file.
     *
     * @param path The path to the JAR or ZIP file.
     * @return The result containing both hashes.
     * @throws IOException If the file cannot be read.
     */
    public static IdentificationResult identify(Path path) throws IOException {
        // 1. Setup SHA-1 Digest
        MessageDigest sha1Digest;
        try {
            sha1Digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found in JVM", e);
        }

        // 2. Setup buffer for CurseForge normalization
        // We must read the file into memory for Murmur2 because the algorithm 
        // requires the full length of the valid data for the tail calculation.
        // Mod files are generally small enough for this to be safe.
        ByteArrayOutputStream normalizedBuffer = new ByteArrayOutputStream();

        // 3. Read file in one pass
        try (BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] ioBuffer = new byte[8192];
            int read;

            while ((read = stream.read(ioBuffer)) != -1) {
                // Feed raw bytes to SHA-1
                sha1Digest.update(ioBuffer, 0, read);

                // Filter bytes for Murmur2 (CurseForge Normalization)
                for (int i = 0; i < read; i++) {
                    byte b = ioBuffer[i];
                    if (!isWhitespace(b)) {
                        normalizedBuffer.write(b);
                    }
                }
            }
        }

        // 4. Finalize Calculations
        String sha1Hex = bytesToHex(sha1Digest.digest());

        byte[] normalizedData = normalizedBuffer.toByteArray();
        long murmurValue = computeMurmur2(normalizedData, normalizedData.length);

        return new IdentificationResult(murmurValue, sha1Hex);
    }

    /**
     * Checks if a byte represents a whitespace character according to the 
     * normalization rules (9, 10, 13, 32).
     */
    private static boolean isWhitespace(byte b) {
        return b == 9 || b == 10 || b == 13 || b == 32;
    }

    /**
     * Converts a byte array to a hexadecimal string.
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * A custom, independent implementation of the MurmurHash2 algorithm 
     * compatible with the 32-bit unsigned verification.
     * * @param data The byte array to hash.
     * @param length The length of the data.
     * @return The hash value as a long (to ensure unsigned 32-bit range is covered).
     */
    private static long computeMurmur2(byte[] data, int length) {
        final int m = 0x5bd1e995;
        final int r = 24;
        // The seed must be 1 for compatibility
        int seed = 1;

        // Initialize the hash to a 'random' value
        int h = seed ^ length;

        int length4 = length / 4;

        for (int i = 0; i < length4; i++) {
            int i4 = i * 4;
            // Combine 4 bytes into a 32-bit integer (Little Endian)
            int k = (data[i4] & 0xff) |
                    ((data[i4 + 1] & 0xff) << 8) |
                    ((data[i4 + 2] & 0xff) << 16) |
                    ((data[i4 + 3] & 0xff) << 24);

            k *= m;
            k ^= k >>> r;
            k *= m;

            h *= m;
            h ^= k;
        }

        // Handle the remaining bytes
        int offset = length4 * 4;
        switch (length % 4) {
            case 3:
                h ^= (data[offset + 2] & 0xff) << 16;
            case 2:
                h ^= (data[offset + 1] & 0xff) << 8;
            case 1:
                h ^= (data[offset] & 0xff);
                h *= m;
        }

        // Final avalanche
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        // Return as unsigned long
        return h & 0xFFFFFFFFL;
    }
}