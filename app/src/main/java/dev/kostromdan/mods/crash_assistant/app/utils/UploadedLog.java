package dev.kostromdan.mods.crash_assistant.app.utils;

public class UploadedLog {
    private String name;
    private long uploadTime;
    private String url;
    private String deleteToken;

    public UploadedLog(String name, long uploadTime, String url, String deleteToken) {
        this.name = name;
        this.uploadTime = uploadTime;
        this.url = url;
        this.deleteToken = deleteToken;
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
}
