package dev.kostromdan.mods.crash_assistant.app.gui.analysis.gml;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

public class GMLMappingDownloaderImpl {

    private final Consumer<String> logger;
    private final Gson gson;

    private static final String PISTON_META_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public GMLMappingDownloaderImpl(Consumer<String> logger) {
        this.logger = logger;
        this.gson = new Gson();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java GMLMappingDownloaderImpl <minecraft_version> <mcp_version>");
            System.exit(1);
        }
        String mcVersion = args[0];
        String mcpVersion = args[1];

        GMLMappingDownloaderImpl downloader = new GMLMappingDownloaderImpl(System.out::println);
        try {
            downloader.download(mcVersion, mcpVersion);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    public void download(String mcVersion, String mcpVersion) throws IOException, NoSuchAlgorithmException {
        Path cacheDir;
        List<String> oldVersions = Arrays.asList("1.19.2", "1.19.3", "1.19.4");

        if (oldVersions.contains(mcVersion)) {
            // For old versions, save directly to mod_data/gml
            cacheDir = Paths.get("mod_data", "gml");
        } else {
            // For new versions (like 1.20.1+), use a version-specific subdirectory
            cacheDir = Paths.get("mod_data", "gml", mcVersion);
        }

        Files.createDirectories(cacheDir);
        logger.accept("Cache directory created at: " + cacheDir);

        // 1. Download and process Piston Meta
        logger.accept("Downloading version manifest...");
        PistonMeta pistonMeta = getJson(PISTON_META_URL, PistonMeta.class);
        PistonMeta.VersionInfo versionInfo = pistonMeta.versions.stream()
                .filter(v -> mcVersion.equals(v.id))
                .findFirst()
                .orElseThrow(() -> new IOException("Could not find Minecraft version " + mcVersion + " in version manifest."));

        logger.accept("Found version metadata URL: " + versionInfo.url);

        // 2. Download and process the specific version.json
        Path versionJsonPath = cacheDir.resolve("version.json");
        downloadAndVerify(versionInfo.url, versionJsonPath, versionInfo.sha1, "version.json");
        VersionMeta versionMeta = gson.fromJson(new String(Files.readAllBytes(versionJsonPath), StandardCharsets.UTF_8), VersionMeta.class);

        // 3. Download official client mappings
        VersionMeta.Download clientMappings = versionMeta.downloads.get("client_mappings");
        if (clientMappings == null) {
            throw new IOException("Could not find client_mappings in version.json");
        }
        Path officialMappingsPath = cacheDir.resolve("official.txt");
        downloadAndVerify(clientMappings.url, officialMappingsPath, clientMappings.sha1, "official.txt");

        // 4. Download MCPConfig zip
        String mcpConfigUrl = String.format("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/%s-%s/mcp_config-%s-%s.zip", mcVersion, mcpVersion, mcVersion, mcpVersion);
        Path mcpZipPath = cacheDir.resolve("srg.zip");
        logger.accept("Downloading MCPConfig from: " + mcpConfigUrl);
        downloadFile(mcpConfigUrl, mcpZipPath, "MCPConfig zip");
        logger.accept("MCPConfig zip saved to: " + mcpZipPath);

        logger.accept("All necessary mapping files have been downloaded successfully.");
    }

    private <T> T getJson(String urlString, Class<T> classOfT) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download JSON from " + urlString + ". Status code: " + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return gson.fromJson(reader, classOfT);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void downloadAndVerify(String url, Path destination, String expectedSha1, String fileDescription) throws IOException, NoSuchAlgorithmException {
        if (Files.exists(destination)) {
            String actualSha1 = calculateSha1(destination);
            if (expectedSha1.equalsIgnoreCase(actualSha1)) {
                logger.accept(fileDescription + " is up to date. Skipping download.");
                return;
            } else {
                logger.accept("Checksum mismatch for " + fileDescription + ". Re-downloading...");
            }
        }
        downloadFile(url, destination, fileDescription);
        String actualSha1 = calculateSha1(destination);
        if (!expectedSha1.equalsIgnoreCase(actualSha1)) {
            throw new IOException("SHA1 checksum verification failed for " + fileDescription + " downloaded from " + url);
        }
        logger.accept("Verified " + fileDescription + " successfully.");
    }

    private void downloadFile(String urlString, Path destination, String fileDescription) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Failed to download " + fileDescription + " from " + urlString + ". Status code: " + responseCode);
            }

            try (InputStream bodyStream = connection.getInputStream()) {
                InputStream streamToRead;
                String contentEncoding = connection.getContentEncoding();
                if ("gzip".equalsIgnoreCase(contentEncoding)) {
                    streamToRead = new GZIPInputStream(bodyStream);
                } else {
                    streamToRead = bodyStream;
                }
                Files.copy(streamToRead, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String calculateSha1(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                sha1.update(buffer, 0, read);
            }
        }
        byte[] hash = sha1.digest();
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

    // --- GSON Data Classes ---
    private static class PistonMeta {
        List<VersionInfo> versions;
        private static class VersionInfo {
            String id;
            String url;
            String sha1;
        }
    }

    private static class VersionMeta {
        Map<String, Download> downloads;
        private static class Download {
            String sha1;
            String url;
        }
    }
}