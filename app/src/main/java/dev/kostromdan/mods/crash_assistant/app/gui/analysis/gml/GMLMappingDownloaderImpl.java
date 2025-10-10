package dev.kostromdan.mods.crash_assistant.app.gui.analysis.gml;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

public class GMLMappingDownloaderImpl {

    private final Consumer<String> logger;
    private final HttpClient httpClient;
    private final Gson gson;

    private static final String PISTON_META_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public GMLMappingDownloaderImpl(Consumer<String> logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
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


    public void download(String mcVersion, String mcpVersion) throws IOException, InterruptedException, NoSuchAlgorithmException {
        Path cacheDir = Paths.get("mod_data", "gml", mcVersion);
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
        VersionMeta versionMeta = gson.fromJson(Files.readString(versionJsonPath), VersionMeta.class);

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

    private <T> T getJson(String url, Class<T> classOfT) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download JSON from " + url + ". Status code: " + response.statusCode());
        }
        return gson.fromJson(response.body(), classOfT);
    }

    private void downloadAndVerify(String url, Path destination, String expectedSha1, String fileDescription) throws IOException, InterruptedException, NoSuchAlgorithmException {
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

    private void downloadFile(String url, Path destination, String fileDescription) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .setHeader("Accept-Encoding", "gzip")
                .GET().build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download " + fileDescription + " from " + url + ". Status code: " + response.statusCode());
        }

        try (InputStream bodyStream = response.body()) {
            InputStream streamToRead = response.headers().firstValue("Content-Encoding").map("gzip"::equalsIgnoreCase).orElse(false)
                    ? new GZIPInputStream(bodyStream)
                    : bodyStream;
            Files.copy(streamToRead, destination, StandardCopyOption.REPLACE_EXISTING);
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