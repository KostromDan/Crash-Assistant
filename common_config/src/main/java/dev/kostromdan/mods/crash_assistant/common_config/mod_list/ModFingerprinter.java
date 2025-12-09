package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private static final int BUFFER_SIZE = 64 * 1024;

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
        // 1. Pass 1: Count normalized length ONLY (Lightweight)
        int normalizedLength = 0;
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(path), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (!isWhitespace(buffer[i])) {
                        normalizedLength++;
                    }
                }
            }
        }

        // 2. Pass 2: Compute SHA-1 (on raw) and Murmur2 (on filtered)
        MessageDigest sha1Digest;
        try {
            sha1Digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found in JVM", e);
        }

        long murmurValue = computeMurmur2AndSha1Streamed(path, normalizedLength, sha1Digest);
        String sha1Hex = bytesToHex(sha1Digest.digest());

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
     * A stream-based implementation of the MurmurHash2 algorithm.
     * Reads the file again to compute the MurmurHash AND updates the SHA-1 digest.
     * * @param path The file path to read.
     *
     * @param length     The total length of non-whitespace bytes (determined in pass 1).
     * @param sha1Digest The SHA-1 digest to update with raw bytes.
     * @return The hash value as a long (to ensure unsigned 32-bit range is covered).
     */
    private static long computeMurmur2AndSha1Streamed(Path path, int length, MessageDigest sha1Digest) throws IOException {
        final int m = 0x5bd1e995;
        final int r = 24;
        // The seed must be 1 for compatibility
        int seed = 1;

        // Initialize the hash to a 'random' value
        int h = seed ^ length;

        try (InputStream stream = new BufferedInputStream(Files.newInputStream(path), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE]; // File read buffer
            int read;

            // We need to form 4-byte chunks from filtered data
            byte[] chunkBuffer = new byte[4];
            int chunkIndex = 0;

            while ((read = stream.read(buffer)) != -1) {
                // Feed RAW bytes to SHA-1
                sha1Digest.update(buffer, 0, read);

                // Filter bytes for Murmur2
                for (int i = 0; i < read; i++) {
                    byte b = buffer[i];
                    if (isWhitespace(b)) continue;

                    chunkBuffer[chunkIndex++] = b;

                    if (chunkIndex == 4) {
                        // Process 4-byte chunk
                        // Combine 4 bytes into a 32-bit integer (Little Endian)
                        int k = (chunkBuffer[0] & 0xff) |
                                ((chunkBuffer[1] & 0xff) << 8) |
                                ((chunkBuffer[2] & 0xff) << 16) |
                                ((chunkBuffer[3] & 0xff) << 24);

                        k *= m;
                        k ^= k >>> r;
                        k *= m;

                        h *= m;
                        h ^= k;

                        chunkIndex = 0;
                    }
                }
            }

            // Handle the remaining bytes
            // chunkIndex is now the number of bytes remaining (0, 1, 2, or 3)
            // matching length % 4
            if (chunkIndex > 0) {
                switch (chunkIndex) {
                    case 3:
                        h ^= (chunkBuffer[2] & 0xff) << 16;
                    case 2:
                        h ^= (chunkBuffer[1] & 0xff) << 8;
                    case 1:
                        h ^= (chunkBuffer[0] & 0xff);
                        h *= m;
                }
            }
        }

        // Final avalanche
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        // Return as unsigned long
        return h & 0xFFFFFFFFL;
    }
}
