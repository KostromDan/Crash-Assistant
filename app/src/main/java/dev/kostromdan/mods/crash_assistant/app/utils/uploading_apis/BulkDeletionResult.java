package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

public class BulkDeletionResult {
    private final boolean success;
    private final String id;
    private final int status;
    private final String error;

    public BulkDeletionResult(boolean success, String id, int status, String error) {
        this.success = success;
        this.id = id;
        this.status = status;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getId() {
        return id;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }
}
