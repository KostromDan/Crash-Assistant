package dev.kostromdan.mods.crash_assistant.app.utils;

public class UploadedLog {
    private String name;
    private long uploadTime;
    private String url;
    private String deleteToken;
    private String arrayToken;
    private boolean metadataDeleted;

    public UploadedLog(String name, long uploadTime, String url, String deleteToken, String arrayToken) {
        this.name = name;
        this.uploadTime = uploadTime;
        this.url = url;
        this.deleteToken = deleteToken;
        this.arrayToken = arrayToken;
    }

    public String getName() {
        return name;
    }

    public long getUploadTime() {
        return uploadTime;
    }

    public String getUrl() {
        return url;
    }

    public String getDeleteToken() {
        return deleteToken;
    }

    public String getArrayToken() {
        return arrayToken;
    }

    public String getLogId() {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public boolean canDeleteFromMcLogs() {
        return deleteToken != null && !deleteToken.trim().isEmpty();
    }

    public boolean canDeleteMetadata() {
        return arrayToken != null && !arrayToken.trim().isEmpty();
    }

    public void markDeletedFromMcLogs() {
        deleteToken = null;
    }

    public void markMetadataDeleted() {
        arrayToken = null;
        metadataDeleted = true;
    }

    public boolean wasMetadataDeleted() {
        return metadataDeleted;
    }

    public boolean isFullyDeleted() {
        return !canDeleteFromMcLogs() && !canDeleteMetadata();
    }
}
