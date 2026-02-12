package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import java.util.List;

public class BulkLogDeletionResponse {
    private final boolean success;
    private final List<BulkDeletionResult> results;
    private final String error;

    public BulkLogDeletionResponse(boolean success, List<BulkDeletionResult> results, String error) {
        this.success = success;
        this.results = results;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<BulkDeletionResult> getResults() {
        return results;
    }

    public String getError() {
        return error;
    }
}
