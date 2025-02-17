package dev.kostromdan.mods.crash_assistant.app.utils;

import gs.mclo.api.MclogsClient;

public class McLogsApiProvider {
    private static MclogsClient MCLogsClient = null;

    public static synchronized MclogsClient getMcLogsClient() {
        if (MCLogsClient == null) {
            MCLogsClient = new MclogsClient("CrashAssistant");
        }
        return MCLogsClient;
    }

}
