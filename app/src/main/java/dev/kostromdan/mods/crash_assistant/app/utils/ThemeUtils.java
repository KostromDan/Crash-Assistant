package dev.kostromdan.mods.crash_assistant.app.utils;

import com.formdev.flatlaf.IntelliJTheme;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ThemeUtils {
    public static boolean applied = false;

    public static synchronized void ensureThemesApplied() {
        if (applied) return;
        applied = true;

        String fileName = CrashAssistantConfig.get("gui_customisation.theme_file_name");
        Path themePath = Paths.get("config", "crash_assistant", fileName);

        if (!Files.isRegularFile(themePath)) {
            CrashAssistantApp.LOGGER.warn("Theme file \"{}\" does not exist in the \"local/crash_assistant\" directory, themes will be disabled.", fileName);
            return;
        }

        try (InputStream is = Files.newInputStream(themePath)) {
            IntelliJTheme.setup(is);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to load custom theme: " + fileName, e);
        }
    }
}