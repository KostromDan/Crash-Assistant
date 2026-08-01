package dev.kostromdan.mods.crash_assistant.app.gui;

import java.awt.Image;
import java.lang.reflect.Method;

/**
 * Java 8 implementation, replaced by the Taskbar implementation in the Java 9+ multi-release JAR.
 */
final class MacOSDockIcon {
    private MacOSDockIcon() {
    }

    static boolean install(Image image) throws Exception {
        Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
        Method getApplication = applicationClass.getMethod("getApplication");
        Method setDockIconImage = applicationClass.getMethod("setDockIconImage", Image.class);
        Object application = getApplication.invoke(null);
        setDockIconImage.invoke(application, image);
        return true;
    }
}
