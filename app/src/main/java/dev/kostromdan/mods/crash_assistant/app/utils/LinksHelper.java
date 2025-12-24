package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public class LinksHelper {
    public static void browse(URI uri) {
        try {
            if (PlatformHelp.isLinux()) {
                browseLinux(uri);
                return;
            }

            Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open link: ", e);
        }
    }

    private static void browseLinux(URI uri) throws IOException {
        String stringUri = uri.toString();

        if ("file".equals(uri.getScheme())) {
            stringUri = stringUri.replace("file:", "file://");
        }

        String[] command = new String[]{"xdg-open", stringUri};

        Process process = Runtime.getRuntime().exec(command);

        process.getInputStream().close();
        process.getErrorStream().close();
        process.getOutputStream().close();
    }
}