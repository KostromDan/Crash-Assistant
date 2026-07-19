package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MclogMetadataApi {
    private static final String DEFAULT_DELETE_API_URL =
            "https://api.kostromdan.dev/v1/crash-assistant/mclog-arrays/delete";

    public CompletableFuture<MetadataDeletionResponse> bulkDelete(Collection<String> arrayTokens) {
        Set<String> uniqueTokens = new LinkedHashSet<>();
        for (String token : arrayTokens) {
            if (token != null && !token.trim().isEmpty()) {
                uniqueTokens.add(token.trim());
            }
        }
        if (uniqueTokens.isEmpty()) {
            return CompletableFuture.completedFuture(new MetadataDeletionResponse(true, 0, 0, null));
        }
        if (uniqueTokens.size() > 1024) {
            return CompletableFuture.completedFuture(new MetadataDeletionResponse(
                    false,
                    0,
                    0,
                    "Too many metadata groups selected (maximum 1024)."));
        }

        return CompletableFuture.supplyAsync(() -> delete(uniqueTokens));
    }

    private MetadataDeletionResponse delete(Set<String> arrayTokens) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(getDeleteApiUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "CrashAssistant");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);

            JsonArray tokens = new JsonArray();
            for (String token : arrayTokens) {
                tokens.add(token);
            }
            JsonObject request = new JsonObject();
            request.add("arrayTokens", tokens);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, responseCode);
            if (responseCode >= 200 && responseCode < 300) {
                JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
                boolean success = "ok".equals(response.get("status").getAsString());
                return new MetadataDeletionResponse(
                        success,
                        response.get("tokensProcessed").getAsInt(),
                        response.get("arraysDeleted").getAsInt(),
                        success ? null : "Metadata deletion was rejected by the server.");
            }
            return new MetadataDeletionResponse(
                    false,
                    0,
                    0,
                    "HTTP Error: " + responseCode + (responseBody.isEmpty() ? "" : "\n" + responseBody));
        } catch (Exception exception) {
            return new MetadataDeletionResponse(false, 0, 0, "Exception: " + exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readResponseBody(HttpURLConnection connection, int responseCode) {
        InputStream stream;
        try {
            stream = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        } catch (Exception ignored) {
            return "";
        }
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        } catch (Exception ignored) {
        }
        return body.toString();
    }

    private static String getDeleteApiUrl() {
        return System.getProperty("crash_assistant.mclog_array_delete_api_url", DEFAULT_DELETE_API_URL);
    }
}
