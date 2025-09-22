package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import com.google.gson.*;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ErrorUtils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the UploadingApi interface for mclo.gs
 */
public class McLogsApi implements UploadingApi {
    private static final String API_BASE_URL = "https://api.mclo.gs/1/";
    private static final String USER_AGENT = "CrashAssistant";
    private static final Gson GSON = new Gson();

    private final String userAgent;

    /**
     * Creates a new McLogsApi with the default user agent
     */
    public McLogsApi() {
        this(USER_AGENT);
    }

    /**
     * Creates a new McLogsApi with a custom user agent
     *
     * @param userAgent The user agent to use for API requests
     */
    public McLogsApi(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public CompletableFuture<UploadLogResponse> uploadLog(String text, Consumer<Integer> onProgressChanged) {
        final String finalText = text.isEmpty() ? "Log is empty." : McLogsAntiVersionCensorer.apply(text);
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Call onProgressChanged with initial progress
                if (onProgressChanged != null) onProgressChanged.accept(0);


                URL url = new URL(API_BASE_URL + "log");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);

                // Prepare the request body
                String content = "content=" + URLEncoder.encode(finalText, StandardCharsets.UTF_8.name());
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                int totalBytes = contentBytes.length;

                // Tell the server how much we will send
                connection.setRequestProperty("Content-Length", String.valueOf(totalBytes));

                // --- real progress reporting ------------------------------------------------
                if (onProgressChanged != null) onProgressChanged.accept(0); // start

                try (OutputStream os = connection.getOutputStream()) {
                    final int MIN_CHUNK = 4 * 1024;     //  4 KiB
                    final int MAX_CHUNK = 64 * 1024;    // 64 KiB

                    int bytesPerPercent = (int) Math.ceil(totalBytes / 100.0);

                    int chunkSize = Integer.highestOneBit(bytesPerPercent);

                    while (chunkSize > bytesPerPercent && chunkSize > MIN_CHUNK) {
                        chunkSize >>= 1;
                    }

                    chunkSize = Math.max(MIN_CHUNK, Math.min(MAX_CHUNK, chunkSize));

                    int bytesWritten = 0;
                    int lastPercent = 0;

                    while (bytesWritten < totalBytes) {
                        int len = Math.min(chunkSize, totalBytes - bytesWritten);
                        os.write(contentBytes, bytesWritten, len);
                        bytesWritten += len;

                        int percent = (int) ((bytesWritten * 100L) / totalBytes);
                        if (percent > lastPercent && onProgressChanged != null) {
                            onProgressChanged.accept(percent);
                            lastPercent = percent;
                        }
                    }
                    os.flush();
                }
                // ---------------------------------------------------------------------------

                // Get the response
                int responseCode = connection.getResponseCode();

                if (onProgressChanged != null) onProgressChanged.accept(100);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Parse the response
                    StringBuilder responseBody = new StringBuilder();
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBody.append(line);
                        }
                    }
                    JsonObject jsonResponse = JsonParser.parseString(responseBody.toString()).getAsJsonObject();

                    if (jsonResponse.get("success").getAsBoolean()) {
                        String id = jsonResponse.get("id").getAsString();
                        String responseUrl = jsonResponse.get("url").getAsString();
                        String rawUrl = jsonResponse.get("raw").getAsString();

                        return new UploadLogResponse(responseUrl, rawUrl, id);
                    } else {
                        String error = jsonResponse.get("error").getAsString();
                        return new UploadLogResponse(error);
                    }
                } else {
                    return new UploadLogResponse("HTTP error: " + responseCode);
                }
            } catch (Exception e) {
                return new UploadLogResponse("Error while uploading log to mclo.gs:\n" + ErrorUtils.getErrorMessageAndStackTrace(e));
            }
        });
    }

    @Override
    public CompletableFuture<UploadLogResponse> uploadLog(String text) {
        return uploadLog(text, null);
    }

    @Override
    public CompletableFuture<LogAnalysisResponse> getProblemsAnalysis(String logUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Extract the ID from the URL
                Pattern pattern = Pattern.compile("https://mclo\\.gs/([A-Za-z0-9]+)");
                Matcher matcher = pattern.matcher(logUrl);

                if (!matcher.find()) {
                    return new LogAnalysisResponse("Invalid log URL format");
                }

                String id = matcher.group(1);
                URL url = new URL(API_BASE_URL + "insights/" + id);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", userAgent);

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Parse the response
                    StringBuilder responseBody = new StringBuilder();
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBody.append(line);
                        }
                    }
                    JsonObject jsonResponse = JsonParser.parseString(responseBody.toString()).getAsJsonObject();

                    if (jsonResponse.has("analysis") && jsonResponse.getAsJsonObject("analysis").has("problems")) {
                        JsonArray problemsArray = jsonResponse.getAsJsonObject("analysis").getAsJsonArray("problems");
                        List<Problem> problems = new ArrayList<>();

                        for (JsonElement problemElement : problemsArray) {
                            JsonObject problemObject = problemElement.getAsJsonObject();
                            String problemMessage = problemObject.get("message").getAsString();

                            // Get the line number
                            int lineNumber = 0;
                            if (problemObject.has("entry") &&
                                    problemObject.getAsJsonObject("entry").has("lines") &&
                                    problemObject.getAsJsonObject("entry").getAsJsonArray("lines").size() > 0) {
                                lineNumber = problemObject.getAsJsonObject("entry")
                                        .getAsJsonArray("lines")
                                        .get(0)
                                        .getAsJsonObject()
                                        .get("number")
                                        .getAsInt();
                            }

                            // Get the solutions
                            List<String> solutions = new ArrayList<>();
                            StringBuilder solutionBuilder = new StringBuilder();
                            if (problemObject.has("solutions")) {
                                JsonArray solutionsArray = problemObject.getAsJsonArray("solutions");
                                for (JsonElement solutionElement : solutionsArray) {
                                    if (solutionBuilder.length() > 0) {
                                        solutionBuilder.append("\n");
                                    }
                                    solutions.add(solutionElement.getAsJsonObject().get("message").getAsString());
                                }
                            }

                            problems.add(new Problem(lineNumber, problemMessage, solutions));
                        }

                        return new LogAnalysisResponse(problems);
                    } else {
                        return new LogAnalysisResponse("No problems found in the log");
                    }
                } else {
                    return new LogAnalysisResponse("HTTP error: " + responseCode);
                }
            } catch (Exception e) {
                return new LogAnalysisResponse("Error: " + e.getMessage());
            }
        });
    }
}
