package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class LinksHelper {
    public static void browse(URI uri) {
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to open link: ", e);
        }
    }
}
