package dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import org.apache.commons.jexl3.annotations.NoJexl;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Analysis {
    // Map to store warnings for each log (or null for global) to prevent duplicates.
    private static final Map<Log, Set<String>> registeredWarnings = Collections.synchronizedMap(new HashMap<>());

    // --- Script persistence and execution control ---

    /**
     * Map to store data that needs to persist between script executions.
     * <p>
     * Scripts are executed within a context that might be recreated or cleared.
     * Furthermore, the log analysis process might be restarted (e.g., after discovering new logs like win.aventa).
     * <p>
     * This storage allows scripts to save state (flags, counters, computed results)
     * effectively escaping the "garbage collection" of their local variables.
     */
    private static final Map<String, Object> GLOBAL_DATA = new ConcurrentHashMap<>();

    /**
     * Set of script names that have requested to be executed on every pass.
     * <p>
     * By default, scripts are executed only once to prevent duplicate alerts or side effects
     * when the log analysis is restarted (triggering a re-run of scripts).
     * <p>
     * However, some scripts might need to run again if they rely on data that becomes available
     * in the second pass (e.g., analysis of WinEventLogs discovered after the first pass).
     * Calling {@link #markRunAlways()} adds the script to this set.
     */
    private static final Set<String> ALWAYS_RUN_SCRIPTS = ConcurrentHashMap.newKeySet();

    /**
     * The name of the script currently being executed.
     * Used to identify which script is calling {@link #markRunAlways()}.
     */
    private static String currentScriptName;

    /**
     * Sets the name of the script currently running.
     * Called by ScriptManager before executing a script.
     *
     * @param name The file name of the script.
     */
    @NoJexl
    public static void setCurrentScriptName(String name) {
        currentScriptName = name;
    }

    /**
     * Checks if a script is marked to run always.
     *
     * @param scriptName The file name of the script.
     * @return true if the script should run every time runAnalysisScripts is called.
     */
    @NoJexl
    public static boolean isAlwaysRun(String scriptName) {
        return ALWAYS_RUN_SCRIPTS.contains(scriptName);
    }

    /**
     * Marks the current script to be executed on every analysis pass.
     * <p>
     * Use this if your script needs to react to changes in the second pass of analysis
     * (e.g., checking WinEventLogs that were not present in the first pass).
     */
    public static void markRunAlways() {
        if (currentScriptName != null) {
            ALWAYS_RUN_SCRIPTS.add(currentScriptName);
        }
    }

    /**
     * Stores a value in the global script storage.
     * Persists across analysis restarts.
     *
     * @param key   The unique key for the data.
     * @param value The value to store.
     */
    public static void setGlobal(String key, Object value) {
        GLOBAL_DATA.put(key, value);
    }

    /**
     * Retrieves a value from the global script storage.
     *
     * @param key The key to retrieve.
     * @return The stored value, or null if not found.
     */
    public static Object getGlobal(String key) {
        return GLOBAL_DATA.get(key);
    }

    /**
     * Adds a global warning to the analysis results.
     * This warning will not be attached to any specific log file.
     *
     * @param message The warning message to display.
     */
    public static void addWarning(String message) {
        addWarning(null, message);
    }

    /**
     * Adds a warning attached to a specific log file.
     * Duplicate warnings for the same log are automatically filtered out.
     *
     * @param log     The log file this warning belongs to.
     * @param message The warning message to display.
     */
    public static void addWarning(Log log, String message) {
        Set<String> warnings = registeredWarnings.computeIfAbsent(log, k -> Collections.synchronizedSet(new HashSet<>()));
        if (!warnings.add(message)) {
            return;
        }

        LogType type = log != null ? log.getType() : LogType.LOG;
        KnownCrashReason reason = new ScriptedAnalysis(type, message);
        KnownCrashReasonMessage.addCrashReasonMessage(new KnownCrashReasonMessage(log, reason));
    }

    /**
     * Retrieves a localized string based on the current language configuration.
     * Useful for formatting warning messages in the user's language.
     *
     * @param key The localization key (e.g., "warning.piracy").
     * @return The localized string, or the key itself if not found.
     */
    public static String lang(String key) {
        return LanguageProvider.get(key);
    }
}
