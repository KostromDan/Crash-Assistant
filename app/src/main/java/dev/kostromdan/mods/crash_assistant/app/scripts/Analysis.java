package dev.kostromdan.mods.crash_assistant.app.scripts;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.GeneratedMessage;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptUtils;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Analysis extends ScriptUtils {
    private static final Map<Log, List<ScriptWarning>> registeredWarnings = Collections.synchronizedMap(new HashMap<>());

    /**
     * Adds a global warning to the analysis results.
     * This warning will not be attached to any specific log file.
     *
     * @param message The warning message to display.
     * @return The created ScriptWarning instance.
     */
    public static ScriptWarning addWarning(String message) {
        return addWarning(null, message);
    }

    /**
     * Adds a warning attached to a specific log file.
     * Duplicate warnings for the same log (by message) are filtered out.
     *
     * @param log     The log file this warning belongs to.
     * @param message The warning message to display.
     * @return The created ScriptWarning instance for further configuration.
     */
    public static ScriptWarning addWarning(Log log, String message) {
        List<ScriptWarning> warnings = registeredWarnings.computeIfAbsent(log, k -> Collections.synchronizedList(new ArrayList<>()));
        
        synchronized (warnings) {
            for (ScriptWarning w : warnings) {
                if (w.getMessage().equals(message)) {
                    return w;
                }
            }
            
            ScriptWarning warning = new ScriptWarning(message);
            warnings.add(warning);
            return warning;
        }
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
    
    public static Map<Log, List<ScriptWarning>> getRegisteredWarnings() {
        return registeredWarnings;
    }
}
