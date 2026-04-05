package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

/**
 * Represents a response from uploading a log
 */
public class UploadLogResponse {
    private boolean success;
    private String url;
    private String rawUrl;
    private String error;
    private String id;
    private LogAnalysisResponse analysisResponse;
    private boolean networkError;

    /**
     * Creates a new successful UploadLogResponse
     * 
     * @param url The URL of the uploaded log
     * @param rawUrl The URL of the raw log content
     * @param id The ID of the uploaded log
     * @param analysisResponse The analysis response from the log service
     */
    public UploadLogResponse(String url, String rawUrl, String id, LogAnalysisResponse analysisResponse) {
        this.success = true;
        this.url = url;
        this.rawUrl = rawUrl;
        this.id = id;
        this.analysisResponse = analysisResponse;
    }

    /**
     * Creates a new failed UploadLogResponse
     * 
     * @param error The error message
     */
    public UploadLogResponse(String error) {
        this(error, false);
    }

    public UploadLogResponse(String error, boolean networkError) {
        this.success = false;
        this.error = error;
        this.networkError = networkError;
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

    public boolean isNetworkError() {
        return networkError;
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
     * Gets the insights for the uploaded log
     * 
     * @return The insights analysis response
     */
    public LogAnalysisResponse getInsights() {
        return analysisResponse;
    }
}
