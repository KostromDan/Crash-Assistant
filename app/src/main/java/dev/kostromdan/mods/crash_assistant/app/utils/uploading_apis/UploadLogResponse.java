package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a response from uploading a log
 */
public class UploadLogResponse {
    private boolean success;
    private String url;
    private String rawUrl;
    private String error;
    private String id;
    private UploadingApi client;

    /**
     * Creates a new successful UploadLogResponse
     * 
     * @param url The URL of the uploaded log
     * @param rawUrl The URL of the raw log content
     * @param id The ID of the uploaded log
     */
    public UploadLogResponse(String url, String rawUrl, String id) {
        this.success = true;
        this.url = url;
        this.rawUrl = rawUrl;
        this.id = id;
    }

    /**
     * Creates a new failed UploadLogResponse
     * 
     * @param error The error message
     */
    public UploadLogResponse(String error) {
        this.success = false;
        this.error = error;
    }

    /**
     * Checks if the upload was successful
     * 
     * @return true if the upload was successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the URL of the uploaded log
     * 
     * @return The URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the URL of the raw log content
     * 
     * @return The raw URL
     */
    public String getRawUrl() {
        return rawUrl;
    }

    /**
     * Gets the error message if the upload failed
     * 
     * @return The error message
     */
    public String getError() {
        return error;
    }

    /**
     * Gets the ID of the uploaded log
     * 
     * @return The ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the client that was used to upload the log
     * 
     * @param client The client
     */
    public void setClient(UploadingApi client) {
        this.client = client;
    }

    /**
     * Gets the insights for the uploaded log
     * 
     * @return A CompletableFuture that will complete with the insights
     */
    public CompletableFuture<LogAnalysisResponse> getInsights() {
        if (client == null) {
            throw new IllegalStateException("Client not set");
        }
        return client.getProblemsAnalysis(url);
    }
}