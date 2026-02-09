package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

public class LogDeletionResponse {
    private final DeletionResult result;
    private final String message;

    public LogDeletionResponse(DeletionResult result, String message) {
        this.result = result;
        this.message = message;
    }

    public DeletionResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }
}
