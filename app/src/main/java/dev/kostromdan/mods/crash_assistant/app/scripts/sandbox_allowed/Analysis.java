package dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Analysis {
    // Map to store warnings for each log (or null for global) to prevent duplicates.
    private static final Map<Log, Set<String>> registeredWarnings = Collections.synchronizedMap(new HashMap<>());

    public static void addWarning(String message) {
        addWarning(null, message);
    }

    public static void addWarning(Log log, String message) {
        Set<String> warnings = registeredWarnings.computeIfAbsent(log, k -> Collections.synchronizedSet(new HashSet<>()));
        if (!warnings.add(message)) {
            return;
        }

        LogType type = log != null ? log.getType() : LogType.LOG;
        KnownCrashReason reason = new ScriptedAnalysis(type, message);
        KnownCrashReasonMessage.addCrashReasonMessage(new KnownCrashReasonMessage(log, reason));
    }

    public static String lang(String key) {
        return LanguageProvider.get(key);
    }
}
