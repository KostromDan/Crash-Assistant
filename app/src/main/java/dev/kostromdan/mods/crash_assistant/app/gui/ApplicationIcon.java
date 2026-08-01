package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.SwingEDT;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.awt.Image;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Creates all native application icons from Java2D code generated from the project SVG. */
final class ApplicationIcon {
    // Covers Windows title bar/taskbar sizes and common freedesktop/X11 icon sizes.
    private static final int[] WINDOW_ICON_SIZES = {
            16, 20, 22, 24, 32, 40, 48, 64, 96, 128, 256
    };
    private static final int MACOS_DOCK_ICON_SIZE = 512;

    private static volatile List<Image> windowIconImages;
    private static volatile Image macOSDockIconImage;
    private static boolean macOSDockIconAttempted;

    private ApplicationIcon() {
    }

    static void install(Window window) {
        try {
            SwingEDT.runAndWait(() -> {
                // On Linux, AWT publishes this list to the native window manager as _NET_WM_ICON.
                // Windows likewise selects the best image for each title bar/taskbar DPI and size.
                List<Image> images = getWindowIconImages();
                window.setIconImages(images);
                installMacOSDockIcon();
            });
        } catch (RuntimeException | LinkageError error) {
            CrashAssistantApp.LOGGER.error("Failed to install the generated application icon", error);
        }
    }

    private static List<Image> getWindowIconImages() {
        List<Image> images = windowIconImages;
        if (images != null) {
            return images;
        }

        synchronized (ApplicationIcon.class) {
            if (windowIconImages == null) {
                List<Image> renderedImages = new ArrayList<Image>(WINDOW_ICON_SIZES.length);
                for (int size : WINDOW_ICON_SIZES) {
                    renderedImages.add(GeneratedLogoIcon.ICON.createMultiResolutionImage(size));
                }
                windowIconImages = Collections.unmodifiableList(renderedImages);
            }
            return windowIconImages;
        }
    }

    private static Image getMacOSDockIconImage() {
        Image image = macOSDockIconImage;
        if (image != null) {
            return image;
        }

        synchronized (ApplicationIcon.class) {
            if (macOSDockIconImage == null) {
                // 512 logical pixels plus a 2x (1024 px) variant for Retina displays.
                macOSDockIconImage = GeneratedLogoIcon.ICON
                        .createMultiResolutionImage(MACOS_DOCK_ICON_SIZE);
            }
            return macOSDockIconImage;
        }
    }

    private static void installMacOSDockIcon() {
        if (!PlatformHelp.isMacOS() || macOSDockIconAttempted) {
            return;
        }

        macOSDockIconAttempted = true;
        try {
            if (!MacOSDockIcon.install(getMacOSDockIconImage())) {
                CrashAssistantApp.LOGGER.warn("The current macOS runtime does not support a custom Dock icon");
            }
        } catch (Exception | LinkageError error) {
            CrashAssistantApp.LOGGER.warn("Failed to install the macOS Dock icon", error);
        }
    }

}
