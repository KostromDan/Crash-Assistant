package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import com.google.gson.*;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLogsManager;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLog;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ErrorUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.SSLException;

/**
 * Implementation of the UploadingApi interface for mclo.gs
 */
public class McLogsApi implements UploadingApi {
    private static final String API_BASE_URL = "https://api.mclo.gs/1/";
    private static final int MAX_CONCURRENT_UPLOADS = 5;
    private static final String USER_AGENT = "CrashAssistant";

    private final String userAgent;
    private final Semaphore uploadSemaphore = new Semaphore(MAX_CONCURRENT_UPLOADS);

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

    private static boolean isNetworkError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnknownHostException ||
                    current instanceof NoRouteToHostException ||
                    current instanceof SocketTimeoutException ||
                    current instanceof SSLException ||
                    current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public CompletableFuture<UploadLogResponse> uploadLog(String logName, String text, Consumer<Integer> onProgressChanged) {
        final String finalText = text.isEmpty() ? "Log is empty." : McLogsAntiVersionCensorer.apply(text);
        return CompletableFuture.supplyAsync(() -> {
            try {
                uploadSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new UploadLogResponse("Upload interrupted during waiting for upload stage.");
            }
            try {
                // Call onProgressChanged with initial progress
                if (onProgressChanged != null) onProgressChanged.accept(0);

                // --- FAKE UPLOADING (For offline testing) ---
//                if (true) {
//                    try {
//                        if (onProgressChanged != null) onProgressChanged.accept(0);
//
//                        // Simulate network latency
//                        for (int i = 1; i <= 10; i++) {
//                            Thread.sleep(100 + (int) (Math.random() * 200));
//                            if (onProgressChanged != null) onProgressChanged.accept(i * 10);
//                        }
//
//                        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
//                        StringBuilder sb = new StringBuilder();
//                        java.util.Random rnd = new java.util.Random();
//                        while (sb.length() < 7) {
//                            sb.append(chars.charAt(rnd.nextInt(chars.length())));
//                        }
//                        String fakeId = sb.toString();
//
//                        String fakeUrl = "https://mclo.gs/" + fakeId;
//                        String fakeRaw = "https://api.mclo.gs/1/raw/" + fakeId;
//
//                        UploadedLogsManager.saveLog(logName, fakeUrl, "fake-token-" + fakeId);
//
//                        LogAnalysisResponse fakeAnalysis = new LogAnalysisResponse("Offline simulation: No issues found.");
//
//                        return new UploadLogResponse(fakeUrl, fakeRaw, fakeId, fakeAnalysis);
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                        return new UploadLogResponse("Fake upload interrupted.");
//                    } finally {
//                        uploadSemaphore.release();
//                    }
//                }


                URL url = new URL(API_BASE_URL + "log?insights=true");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                // Indicate that the content is GZIP compressed
                connection.setRequestProperty("Content-Encoding", "gzip");
                connection.setDoOutput(true);

                // Prepare the request body
                String content = "content=" + URLEncoder.encode(finalText, StandardCharsets.UTF_8.name());
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

                // Compress the content using GZIP
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                    gzipOutputStream.write(contentBytes);
                }
                byte[] compressedBytes = byteArrayOutputStream.toByteArray();

                int totalBytes = compressedBytes.length;

                // Tell the server how much we will send (compressed size)
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
                        // Write the compressed bytes
                        os.write(compressedBytes, bytesWritten, len);
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
                        String token = jsonResponse.has("token") ? jsonResponse.get("token").getAsString() : null;

                        if (token != null) {
                            UploadedLogsManager.saveLog(logName, responseUrl, token);
                        }

                        LogAnalysisResponse analysisResponse;
                        if (jsonResponse.has("content") && jsonResponse.getAsJsonObject("content").has("insights")) {
                            JsonObject insights = jsonResponse.getAsJsonObject("content").getAsJsonObject("insights");
                            if (insights.has("analysis") && insights.getAsJsonObject("analysis").has("problems")) {
                                JsonArray problemsArray = insights.getAsJsonObject("analysis").getAsJsonArray("problems");
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
                                    if (problemObject.has("solutions")) {
                                        JsonArray solutionsArray = problemObject.getAsJsonArray("solutions");
                                        for (JsonElement solutionElement : solutionsArray) {
                                            solutions.add(solutionElement.getAsJsonObject().get("message").getAsString());
                                        }
                                    }

                                    problems.add(new Problem(lineNumber, problemMessage, solutions));
                                }
                                analysisResponse = new LogAnalysisResponse(problems);
                            } else {
                                analysisResponse = new LogAnalysisResponse("No problems found in the log");
                            }
                        } else {
                            analysisResponse = new LogAnalysisResponse("No problems found in the log");
                        }

                        return new UploadLogResponse(responseUrl, rawUrl, id, analysisResponse);
                    } else {
                        String error = jsonResponse.get("error").getAsString();
                        return new UploadLogResponse(error);
                    }
                } else {
                    return new UploadLogResponse("HTTP error: " + responseCode, responseCode >= 500);
                }
            } catch (Exception e) {
                return new UploadLogResponse("Error while uploading log to mclo.gs:\n" + ErrorUtils.getErrorMessageAndStackTrace(e), isNetworkError(e));
            } finally {
                uploadSemaphore.release();
            }
        });
    }

    @Override
    public CompletableFuture<UploadLogResponse> uploadLog(String logName, String text) {
        return uploadLog(logName, text, null);
    }

    @Override
    public CompletableFuture<LogDeletionResponse> deleteLog(String logId, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(API_BASE_URL + "log/" + logId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return new LogDeletionResponse(DeletionResult.SUCCESS, null);
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return new LogDeletionResponse(DeletionResult.NOT_FOUND, "Log not found (404). It might have been already deleted.");
                } else {
                    StringBuilder errorMsg = new StringBuilder();
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorMsg.append(line);
                        }
                    } catch (Exception ignored) {
                    }
                    String message = "HTTP Error: " + responseCode;
                    if (errorMsg.length() > 0) {
                        message += "\nServer message: " + errorMsg.toString();
                    }
                    return new LogDeletionResponse(DeletionResult.ERROR, message);
                }
            } catch (Exception e) {
                return new LogDeletionResponse(DeletionResult.ERROR, "Exception: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<BulkLogDeletionResponse> bulkDeleteLogs(List<UploadedLog> logs) {
        if (logs.isEmpty()) {
            return CompletableFuture.completedFuture(new BulkLogDeletionResponse(true, new ArrayList<>(), null));
        }

        return CompletableFuture.supplyAsync(() -> {
            CrashAssistantApp.LOGGER.info("Starting bulk deletion of {} logs", logs.size());
            List<BulkDeletionResult> allResults = new ArrayList<>();
            List<List<UploadedLog>> partitions = new ArrayList<>();
            for (int i = 0; i < logs.size(); i += 256) {
                partitions.add(logs.subList(i, Math.min(i + 256, logs.size())));
            }

            for (List<UploadedLog> chunk : partitions) {
                try {
                    CrashAssistantApp.LOGGER.debug("Sending bulk delete request for chunk of {} logs", chunk.size());
                    URL url = new URL(API_BASE_URL + "bulk/log/delete");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("User-Agent", userAgent);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);

                    JsonArray jsonBody = new JsonArray();
                    for (UploadedLog log : chunk) {
                        JsonObject obj = new JsonObject();
                        String id = log.getUrl().substring(log.getUrl().lastIndexOf('/') + 1);
                        obj.addProperty("id", id);
                        obj.addProperty("token", log.getDeleteToken());
                        jsonBody.add(obj);
                    }

                    String content = jsonBody.toString();
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(content.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }

                    int responseCode = connection.getResponseCode();
                    CrashAssistantApp.LOGGER.info("Bulk delete chunk response code: {}", responseCode);
                    
                    // 207 Multi-Status is success for bulk operations
                    if (responseCode == 207 || responseCode == HttpURLConnection.HTTP_OK) {
                        StringBuilder responseBody = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseBody.append(line);
                            }
                        }
                        
                        JsonObject jsonResponse = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
                        boolean overallSuccess = jsonResponse.get("success").getAsBoolean();
                        CrashAssistantApp.LOGGER.info("Bulk delete chunk overall success: {}", overallSuccess);

                        if (overallSuccess) {
                            JsonArray results = jsonResponse.getAsJsonArray("results");
                            for (JsonElement resultElem : results) {
                                JsonObject resultObj = resultElem.getAsJsonObject();
                                boolean success = resultObj.get("success").getAsBoolean();
                                String logId = resultObj.get("id").getAsString();
                                if (!success) {
                                    CrashAssistantApp.LOGGER.warn("Failed to delete log {} in bulk request. Error: {}", logId, resultObj.has("error") ? resultObj.get("error").getAsString() : "unknown");
                                }
                                allResults.add(new BulkDeletionResult(
                                    success,
                                    logId,
                                    resultObj.has("status") ? resultObj.get("status").getAsInt() : 0,
                                    resultObj.has("error") ? resultObj.get("error").getAsString() : null
                                ));
                            }
                        } else {
                            String error = jsonResponse.has("error") ? jsonResponse.get("error").getAsString() : "Unknown error";
                            CrashAssistantApp.LOGGER.error("API returned failure for the entire bulk delete chunk: {}", error);
                            for (UploadedLog log : chunk) {
                                String id = log.getUrl().substring(log.getUrl().lastIndexOf('/') + 1);
                                allResults.add(new BulkDeletionResult(false, id, responseCode, error));
                            }
                        }

                    } else {
                        // HTTP Error
                        StringBuilder errorMsg = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                errorMsg.append(line);
                            }
                        } catch (Exception ignored) {}
                        
                        String message = "HTTP Error: " + responseCode;
                        if (errorMsg.length() > 0) {
                            message += "\nServer message: " + errorMsg.toString();
                        }
                        CrashAssistantApp.LOGGER.error("Bulk delete HTTP error: {}", message);
                        
                        for (UploadedLog log : chunk) {
                            String id = log.getUrl().substring(log.getUrl().lastIndexOf('/') + 1);
                            allResults.add(new BulkDeletionResult(false, id, responseCode, message));
                        }
                    }

                } catch (Exception e) {
                    CrashAssistantApp.LOGGER.error("Exception during bulk deletion chunk", e);
                    for (UploadedLog log : chunk) {
                        String id = log.getUrl().substring(log.getUrl().lastIndexOf('/') + 1);
                        allResults.add(new BulkDeletionResult(false, id, 0, "Exception: " + e.getMessage()));
                    }
                }
            }
            CrashAssistantApp.LOGGER.info("Bulk deletion completed. Processed {} total results.", allResults.size());
            return new BulkLogDeletionResponse(true, allResults, null);
        });
    }}
