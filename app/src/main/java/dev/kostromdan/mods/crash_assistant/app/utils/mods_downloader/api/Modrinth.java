package dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal Modrinth API client for fingerprint-based lookups.
 * Sends a single request to {@code /v2/version_files} with SHA-1 hashes
 * and returns download + project links for found entries.
 */
public class Modrinth {
    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "CrashAssistant/1.0";
    private static final Gson GSON = new Gson();

    public static class VersionFileInfo {
        public final String hash;
        public final String projectId;
        public final String versionId;
        public final String fileName;
        public final String downloadUrl;
        public final String projectUrl;

        public VersionFileInfo(String hash, String projectId, String versionId, String fileName, String downloadUrl, String projectUrl) {
            this.hash = hash;
            this.projectId = projectId;
            this.versionId = versionId;
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
            this.projectUrl = projectUrl;
        }
    }

    /**
     * Resolves a batch of SHA-1 hashes via Modrinth {@code /version_files}.
     * Only a single HTTP request is performed; missing hashes are simply absent from the map.
     */
    public static Map<String, VersionFileInfo> lookupVersionFiles(Collection<String> sha1Hashes) throws IOException {
        Map<String, VersionFileInfo> result = new HashMap<>();
        if (sha1Hashes == null || sha1Hashes.isEmpty()) {
            return result;
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<String>();
        for (String hash : sha1Hashes) {
            if (hash != null && !hash.trim().isEmpty()) {
                deduped.add(hash.toLowerCase(Locale.ROOT));
            }
        }
        if (deduped.isEmpty()) {
            return result;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + "/version_files");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setDoOutput(true);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(20000);

            JsonObject payload = new JsonObject();
            JsonArray hashesArray = new JsonArray();
            for (String hash : deduped) {
                hashesArray.add(hash);
            }
            payload.add("hashes", hashesArray);
            payload.addProperty("algorithm", "sha1");

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                CrashAssistantApp.LOGGER.warn("Modrinth lookup failed with HTTP {}", code);
                return result;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject response = GSON.fromJson(sb.toString(), JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
                String hash = entry.getKey() == null ? null : entry.getKey().toLowerCase(Locale.ROOT);
                if (hash == null) continue;
                JsonObject obj = entry.getValue().getAsJsonObject();

                String projectId = getAsString(obj, "project_id");
                String versionId = getAsString(obj, "id");
                String projectUrl = projectId == null ? null : "https://modrinth.com/mod/" + projectId;

                String downloadUrl = null;
                String fileName = null;
                if (obj.has("files") && obj.get("files").isJsonArray()) {
                    JsonArray files = obj.getAsJsonArray("files");
                    JsonObject preferred = null;
                    for (JsonElement fileElement : files) {
                        JsonObject fileObj = fileElement.getAsJsonObject();
                        if (fileObj.has("primary") && fileObj.get("primary").getAsBoolean()) {
                            preferred = fileObj;
                            break;
                        }
                    }
                    if (preferred == null && files.size() > 0) {
                        preferred = files.get(0).getAsJsonObject();
                    }
                    if (preferred != null) {
                        downloadUrl = getAsString(preferred, "url");
                        if (preferred.has("filename")) {
                            fileName = preferred.get("filename").getAsString();
                        }
                    }
                }

                result.put(hash, new VersionFileInfo(hash, projectId, versionId, fileName, downloadUrl, projectUrl));
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to query Modrinth version_files API", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    private static String getAsString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
