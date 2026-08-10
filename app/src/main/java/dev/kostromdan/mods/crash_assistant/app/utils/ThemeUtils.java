package dev.kostromdan.mods.crash_assistant.app.utils;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.LinuxDesktopIntegration;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ThemeUtils {
    private static boolean applied = false;
    private static boolean successfully = false;

    public static synchronized void ensureThemesApplied() {
        if (applied) return;
        applied = true;

        // This method is the common UI initialization barrier. Linux desktop integration must
        // run before FlatLaf or any AWT/Swing component initializes the native toolkit.
        LinuxDesktopIntegration.prepare();

        String themeIdentifier = CrashAssistantConfig.get("gui_customisation.theme_file_name");

        if (tryLoadStandardTheme(themeIdentifier)) {
            successfully = true;
            return;
        }

        Path themePath = Paths.get("config", "crash_assistant", themeIdentifier);
        if (!Files.isRegularFile(themePath)) {
            CrashAssistantApp.LOGGER.warn("Theme \"{}\" is neither a valid class nor a file in \"config/crash_assistant\". Standard Swing L&F will be used.", themeIdentifier);
            return;
        }

        try (InputStream is = Files.newInputStream(themePath)) {
            IntelliJTheme.setup(is);
            successfully = true;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to load custom JSON theme: " + themeIdentifier, e);
        }
    }

    public static boolean isThemeSuccessfullyLoaded() {
        return successfully;
    }

    private static boolean tryLoadStandardTheme(String themeName) {
        if (themeName == null) return false;
        try {
            switch (themeName.toLowerCase()) {
                case "swing":
                    return true;
                case "flatlightlaf":
                    return FlatLightLaf.setup();
                case "flatdarklaf":
                    return FlatDarkLaf.setup();
                case "flatintellijlaf":
                    return FlatIntelliJLaf.setup();
                case "flatdarculalaf":
                    return FlatDarculaLaf.setup();
                case "flatmaclightlaf":
                    return FlatMacLightLaf.setup();
                case "flatmacdarklaf":
                    return FlatMacDarkLaf.setup();
                default:
                    return false;
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error setting up core theme: " + themeName, e);
            return false;
        }
    }
}
