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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CurseForge {
    // By using this API key in your builds, you agree to the Terms and Conditions at:
    // https://support.curseforge.com/en/support/solutions/articles/9000207405-curse-forge-3rd-party-api-terms-and-conditions
    //
    // IMPORTANT: CurseForge requires replacing this key if you create any derivative work, including fork under temporary permission.
    // This API key is issued specifically for the Crash Assistant mod by KostromDan (Daniil Averin).
    private static final String API_KEY = "$2a$10$mUzm1tGasW4kVV7Yw1zjBO9AmFcJClNyjLUsgIZv5E.h/zEcrAlpG";

    private static final String BASE_URL = "https://api.curseforge.com/v1";
    private static final String DOWNLOAD_HOST = "edge.forgecdn.net";
    private static final Gson GSON = new Gson();
    private static final ConcurrentHashMap<Long, SlugInfo> SLUG_CACHE = new ConcurrentHashMap<Long, SlugInfo>();

    public static class FingerprintMatch {
        public final long fingerprint;
        public final long modId;
        public final long fileId;
        public final String fileName;
        public final String downloadUrl; // may be null

        public FingerprintMatch(long fingerprint, long modId, long fileId, String fileName, String downloadUrl) {
            this.fingerprint = fingerprint;
            this.modId = modId;
            this.fileId = fileId;
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
        }

        public boolean hasDownload() {
            return downloadUrl != null && !downloadUrl.trim().isEmpty();
        }
    }

    public static class SlugInfo {
        public final String slug;
        public final String websiteUrl;

        public SlugInfo(String slug, String websiteUrl) {
            this.slug = slug;
            this.websiteUrl = websiteUrl;
        }
    }

    public static void authenticateDownload(HttpURLConnection connection) {
        if (connection != null && DOWNLOAD_HOST.equalsIgnoreCase(connection.getURL().getHost())) {
            connection.setRequestProperty("x-api-key", API_KEY);
        }
    }

    /**
     * Performs a single /fingerprints lookup for a batch of CurseForge Murmur2 hashes.
     */
    public static Map<Long, FingerprintMatch> lookupFingerprints(Collection<Long> fingerprints) throws IOException {
        Map<Long, FingerprintMatch> result = new HashMap<Long, FingerprintMatch>();
        if (fingerprints == null || fingerprints.isEmpty()) {
            return result;
        }

        LinkedHashSet<Long> deduped = new LinkedHashSet<Long>();
        for (Long fp : fingerprints) {
            if (fp != null && fp > 0) {
                deduped.add(fp);
            }
        }
        if (deduped.isEmpty()) {
            return result;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + "/fingerprints");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("x-api-key", API_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);

            JsonObject payload = new JsonObject();
            JsonArray arr = new JsonArray();
            for (Long fp : deduped) {
                arr.add(fp);
            }
            payload.add("fingerprints", arr);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                CrashAssistantApp.LOGGER.warn("CurseForge fingerprint lookup failed with HTTP {}", code);
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
            if (response == null || !response.has("data")) {
                return result;
            }
            JsonObject data = response.getAsJsonObject("data");
            if (!data.has("exactMatches") || !data.get("exactMatches").isJsonArray()) {
                return result;
            }
            JsonArray exactMatches = data.getAsJsonArray("exactMatches");
            for (JsonElement matchEl : exactMatches) {
                JsonObject matchObj = matchEl.getAsJsonObject();
                JsonObject fileObj = matchObj.getAsJsonObject("file");
                if (fileObj == null) continue;
                long fp = fileObj.has("fileFingerprint") ? fileObj.get("fileFingerprint").getAsLong() : -1;
                long modId = fileObj.has("modId") ? fileObj.get("modId").getAsLong() : -1;
                long fileId = fileObj.has("id") ? fileObj.get("id").getAsLong() : -1;
                String downloadUrl = getAsString(fileObj, "downloadUrl");
                String fileName = getAsString(fileObj, "fileName");
                if (fp > 0) {
                    result.put(fp, new FingerprintMatch(fp, modId, fileId, fileName, downloadUrl));
                }
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to query CurseForge fingerprints API", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * Resolves slug/website URL for the given mod id. Cached to avoid repeated hits.
     */
    public static SlugInfo resolveSlug(long modId) {
        if (modId <= 0) return null;
        SlugInfo cached = SLUG_CACHE.get(modId);
        if (cached != null) return cached;

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + "/mods/" + modId);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("x-api-key", API_KEY);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                CrashAssistantApp.LOGGER.warn("CurseForge slug lookup failed for mod {} with HTTP {}", modId, code);
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject response = GSON.fromJson(sb.toString(), JsonObject.class);
            if (response == null || !response.has("data")) return null;
            JsonObject data = response.getAsJsonObject("data");
            String slug = getAsString(data, "slug");
            String websiteUrl = null;
            if (data.has("links") && data.get("links").isJsonObject()) {
                websiteUrl = getAsString(data.getAsJsonObject("links"), "websiteUrl");
            }
            if (slug == null && websiteUrl != null) {
                int idx = websiteUrl.lastIndexOf('/');
                if (idx >= 0 && idx < websiteUrl.length() - 1) {
                    slug = websiteUrl.substring(idx + 1);
                }
            }
            SlugInfo info = new SlugInfo(slug, websiteUrl);
            SLUG_CACHE.put(modId, info);
            return info;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to resolve CurseForge slug for mod " + modId, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
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
