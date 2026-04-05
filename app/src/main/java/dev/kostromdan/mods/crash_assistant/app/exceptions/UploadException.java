package dev.kostromdan.mods.crash_assistant.app.exceptions;

public class UploadException extends RuntimeException {
    private final boolean networkError;

    public UploadException(String message) {
        this(message, false);
    }

    public UploadException(String message, boolean networkError) {
        super(message);
        this.networkError = networkError;
    }

    public boolean isNetworkError() {
        return networkError;
    }

    public static UploadException network(String message) {
        return new UploadException(message, true);
    }
}
