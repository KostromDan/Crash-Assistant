package dev.kostromdan.mods.crash_assistant.app.gui;

import java.awt.Image;
import java.awt.Taskbar;

/**
 * Java 9+ implementation using the public AWT API for the macOS Dock icon.
 */
final class MacOSDockIcon {
    private MacOSDockIcon() {
    }

    static boolean install(Image image) {
        Taskbar taskbar = Taskbar.getTaskbar();
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            return false;
        }
        taskbar.setIconImage(image);
        return true;
    }
}
