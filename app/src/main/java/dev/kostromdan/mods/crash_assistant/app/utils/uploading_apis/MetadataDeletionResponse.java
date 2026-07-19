package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

public class MetadataDeletionResponse {
    private final boolean success;
    private final int tokensProcessed;
    private final int arraysDeleted;
    private final String error;

    public MetadataDeletionResponse(boolean success, int tokensProcessed, int arraysDeleted, String error) {
        this.success = success;
        this.tokensProcessed = tokensProcessed;
        this.arraysDeleted = arraysDeleted;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getTokensProcessed() {
        return tokensProcessed;
    }

    public int getArraysDeleted() {
        return arraysDeleted;
    }

    public String getError() {
        return error;
    }
}
