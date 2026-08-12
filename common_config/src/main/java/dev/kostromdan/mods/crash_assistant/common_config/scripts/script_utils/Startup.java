package dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for startup scripts.
 * Offers methods to add boot-time warnings and potential crash reasons.
 */
public class Startup extends ScriptUtils {
    // Warnings that should be displayed immediately during boot (new process GUI).
    private static final List<ScriptWarning> bootWarnings = Collections.synchronizedList(new ArrayList<>());

    // Warnings that should be "queued" and displayed only if the game crashes later.
    private static final List<ScriptWarning> crashWarnings = Collections.synchronizedList(new ArrayList<>());

    // Flag to crash the game after startup scripts finish
    private static volatile boolean shouldCrash = false;

    /**
     * Adds a warning that will be displayed immediately during startup in a separate window.
     * Use this for critical information that the user MUST see before the game fully loads.
     *
     * @param message The warning message.
     * @return The created ScriptWarning instance for further configuration (priority, buttons).
     */
    public static ScriptWarning addBootWarning(String message) {
        if (message != null && !message.trim().isEmpty()) {
            ScriptWarning warning = new ScriptWarning(message);
            bootWarnings.add(warning);
            return warning;
        }
        return new ScriptWarning(message); // Return dummy if empty to prevent NPE on chain
    }

    /**
     * Adds a warning that will be saved and only displayed IF the game crashes later.
     * This is useful for "soft" errors that might be relevant to a crash report.
     *
     * @param message The warning message.
     * @return The created ScriptWarning instance for further configuration.
     */
    public static ScriptWarning addCrashWarning(String message) {
        if (message != null && !message.trim().isEmpty()) {
            ScriptWarning warning = new ScriptWarning(message);
            crashWarnings.add(warning);
            return warning;
        }
        return new ScriptWarning(message);
    }

    public static void putCopiedResult(String id, String text) {
        GeneratedMessage.putAnalysisResult(id, text);
    }

    public static void putCopiedResult(String id, String text, int priority) {
        GeneratedMessage.putAnalysisResult(id, text, priority);
    }

    public static void removeCopiedResult(String id) {
        GeneratedMessage.removeAnalysisResult(id);
    }

    /**
     * Flags the game to crash after startup scripts complete.
     * The process will exit with code -1 after displaying any boot warnings.
     */
    public static void markForCrash() {
        shouldCrash = true;
        JarInJarHelper.LOGGER.warn("Script {} marked game for crash!", getCurrentScriptName());
        Logger.warn("Script {} marked game for crash!", getCurrentScriptName());
    }

    public static List<ScriptWarning> getBootWarnings() {
        return new ArrayList<>(bootWarnings);
    }

    public static boolean hasBootWarnings() {
        return !bootWarnings.isEmpty();
    }

    public static List<ScriptWarning> getCrashWarnings() {
        return new ArrayList<>(crashWarnings);
    }

    public static boolean isMarkedForCrash() {
        return shouldCrash;
    }
}
