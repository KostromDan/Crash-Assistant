package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

public class ApiProvider {
    private static UploadingApi client = null;

    public static synchronized UploadingApi getMcLogsClient() {
        if (client == null) {
            client = new McLogsApi("CrashAssistant");
        }
        return client;
    }
}
