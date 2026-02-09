package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Abstract interface for log uploading APIs
 */
public interface UploadingApi {
    
    /**
     * Uploads a log to the service
     * 
     * @param logName The name of the log
     * @param text The log text to upload
     * @param onProgressChanged Callback function that will be called when upload progress changes
     * @return A CompletableFuture that will complete with the response containing the URL of the uploaded log
     */
    CompletableFuture<UploadLogResponse> uploadLog(String logName, String text, Consumer<Integer> onProgressChanged);
    
    /**
     * Uploads a log to the service without progress tracking
     * 
     * @param logName The name of the log
     * @param text The log text to upload
     * @return A CompletableFuture that will complete with the response containing the URL of the uploaded log
     */
    CompletableFuture<UploadLogResponse> uploadLog(String logName, String text);

    /**
     * Deletes a log from the service
     *
     * @param logId The ID of the log to delete
     * @param token The deletion token
     * @return A CompletableFuture that will complete with the result of the deletion
     */
    CompletableFuture<LogDeletionResponse> deleteLog(String logId, String token);
}