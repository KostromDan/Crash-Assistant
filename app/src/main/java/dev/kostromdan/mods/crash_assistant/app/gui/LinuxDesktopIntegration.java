package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.imageio.ImageIO;
import java.awt.Toolkit;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Associates Swing windows with a freedesktop application icon used by Linux docks. */
public final class LinuxDesktopIntegration {
    private static final String BOOT_DESKTOP_FILE_NAME =
            "dev.kostromdan.CrashAssistant.desktop";
    private static final String STANDALONE_DESKTOP_FILE_NAME =
            "dev.kostromdan.CrashAssistant.Standalone.desktop";
    private static final String BOOT_WM_CLASS =
            "dev-kostromdan-mods-crash_assistant-app-class_loading-Boot";
    private static final String STANDALONE_WM_CLASS =
            "dev-kostromdan-mods-crash_assistant-app-CrashAssistantApp";
    private static boolean prepared;

    private LinuxDesktopIntegration() {
    }

    /** Must run before any other code initializes AWT on Linux. */
    public static synchronized void prepare() {
        if (!PlatformHelp.isLinux()) return;
        if (prepared) return;
        prepared = true;

        try {
            // OpenJDK derives WM_CLASS from the bottom class of the initialization stack.
            // Initializing here makes it deterministic for either supported entry point.
            System.setProperty("java.desktop.appName", BOOT_DESKTOP_FILE_NAME);
            // Publish both mappings before AWT exposes any X11 windows to the desktop shell.
            installDesktopEntry();
            Toolkit.getDefaultToolkit();
        } catch (RuntimeException | LinkageError | IOException error) {
            CrashAssistantApp.LOGGER.warn(
                    "Failed to install the Linux desktop icon association; window icons still use AWT",
                    error
            );
        }
    }

    private static void installDesktopEntry() throws IOException {
        Path dataHome = getDataHome();
        Path integrationDirectory = dataHome.resolve("crash-assistant");
        Path applicationsDirectory = dataHome.resolve("applications");
        Path iconPath = integrationDirectory.resolve("logo.png").toAbsolutePath();

        Files.createDirectories(integrationDirectory);
        Files.createDirectories(applicationsDirectory);

        ByteArrayOutputStream iconBytes = new ByteArrayOutputStream();
        if (!ImageIO.write(GeneratedLogoIcon.ICON.render(256, 256), "png", iconBytes)) {
            throw new IOException("No PNG writer is available");
        }
        writeIfChanged(iconPath, iconBytes.toByteArray());

        writeIfChanged(applicationsDirectory.resolve(BOOT_DESKTOP_FILE_NAME),
                createDesktopEntry(iconPath, BOOT_WM_CLASS).getBytes(StandardCharsets.UTF_8));
        // The standalone/Parallels JAR starts CrashAssistantApp directly and therefore has a
        // different OpenJDK-generated WM_CLASS. Keeping a hidden entry for each supported entry
        // point lets GNOME associate both without native code or JDK-internal reflection.
        writeIfChanged(applicationsDirectory.resolve(STANDALONE_DESKTOP_FILE_NAME),
                createDesktopEntry(iconPath, STANDALONE_WM_CLASS)
                        .getBytes(StandardCharsets.UTF_8));
    }

    static String createDesktopEntry(Path iconPath) {
        return createDesktopEntry(iconPath, BOOT_WM_CLASS);
    }

    static String createDesktopEntry(Path iconPath, String startupWmClass) {
        return "[Desktop Entry]\n"
                + "Type=Application\n"
                + "Name=Crash Assistant\n"
                + "Comment=Minecraft crash diagnostics\n"
                + "Icon=" + escapeDesktopValue(iconPath.toString()) + "\n"
                + "Exec=/bin/true\n"
                + "Terminal=false\n"
                + "NoDisplay=true\n"
                + "StartupNotify=false\n"
                + "StartupWMClass=" + startupWmClass + "\n"
                + "SingleMainWindow=true\n";
    }

    private static Path getDataHome() {
        String configured = System.getenv("XDG_DATA_HOME");
        if (configured != null && !configured.trim().isEmpty()) {
            Path configuredPath = Paths.get(configured);
            if (configuredPath.isAbsolute()) return configuredPath;
        }
        return Paths.get(System.getProperty("user.home"), ".local", "share");
    }

    private static String escapeDesktopValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void writeIfChanged(Path path, byte[] content) throws IOException {
        if (Files.isRegularFile(path) && Arrays.equals(Files.readAllBytes(path), content)) return;
        Files.write(path, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }
}
