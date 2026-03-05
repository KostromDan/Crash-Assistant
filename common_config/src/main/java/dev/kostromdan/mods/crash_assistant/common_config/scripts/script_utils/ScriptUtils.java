package dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils;

import org.apache.commons.jexl3.annotations.NoJexl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base utility class for scripts to handle global state and execution flags.
 */
public class ScriptUtils {
    // --- Script persistence and execution control ---

    /**
     * Map to store data that needs to persist between script executions.
     * <p>
     * Scripts are executed within a context that might be recreated or cleared.
     * key -> value storage allows scripts to save state effectively escaping the "garbage collection" of their local variables.
     */
    private static final Map<String, Object> GLOBAL_DATA = new ConcurrentHashMap<>();

    /**
     * Set of script names that have requested to be executed on every pass.
     * By default, scripts are executed only once.
     */
    private static final Set<String> ALWAYS_RUN_SCRIPTS = ConcurrentHashMap.newKeySet();

    /**
     * The name of the script currently being executed.
     * Used to identify which script is calling {@link #markRunAlways()}.
     */
    private static String currentScriptName;

    /**
     * Sets the name of the script currently running.
     * Called by AbstractScriptManager before executing a script.
     *
     * @param name The file name of the script.
     */
    @NoJexl
    public static void setCurrentScriptName(String name) {
        currentScriptName = name;
    }

    @NoJexl
    public static String getCurrentScriptName() {
        return currentScriptName;
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
     * Use this if your script needs to react to changes in the second pass of analysis.
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
}
