package dev.kostromdan.mods.crash_assistant.app.scripts;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalyser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.AbstractScriptManager;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AnalysisScriptManager extends AbstractScriptManager {

    private static final AnalysisScriptManager INSTANCE = new AnalysisScriptManager();
    private final Map<ScriptWarning, KnownCrashReasonMessage> publishedWarnings =
            new ConcurrentHashMap<>();

    @Override
    protected Path getScriptsDir() {
        return Paths.get("config", "crash_assistant", "scripts", "log_analysis");
    }

    @Override
    protected JexlContext createContext() {
        JexlContext context = super.createBaseContext();
        
        // Inject LogType enum constants.
        for (LogType type : LogType.values()) {
            context.set(type.name(), type);
        }

        return context;
    }

    public static void runAnalysisScripts() {
        INSTANCE.publishedWarnings.clear();
        try {
            JarInJarHelper.setupScripts();
            INSTANCE.runScripts();
            INSTANCE.publishRegisteredWarningsSafely();
        } finally {
            if (!LogAnalyser.isAnalysisStopped()) {
                INSTANCE.publishedWarnings.values().forEach(message -> message.setProvisional(false));
                INSTANCE.publishedWarnings.clear();
            }
        }
    }

    public static void finalizeCurrentWarningsAtTimeout() {
        INSTANCE.publishedWarnings.values().forEach(message -> message.setProvisional(false));
    }

    @Override
    protected void runScript(Path path, JexlEngine engine, JexlContext context) {
        if (this != INSTANCE) {
            super.runScript(path, engine, context);
            return;
        }
        if (LogAnalyser.isAnalysisStopped()) {
            return;
        }
        try {
            super.runScript(path, engine, context);
        } catch (Throwable throwable) {
            CrashAssistantApp.LOGGER.error("Error while executing analysis script " + path, throwable);
        } finally {
            publishRegisteredWarningsSafely();
        }
    }

    private void publishRegisteredWarningsSafely() {
        if (LogAnalyser.isAnalysisStopped()) {
            return;
        }
        try {
            publishRegisteredWarnings();
        } catch (Throwable throwable) {
            CrashAssistantApp.LOGGER.error("Error while publishing analysis script results", throwable);
        }
    }

    private void publishRegisteredWarnings() {
        Map<Log, List<ScriptWarning>> warnings = Analysis.getRegisteredWarnings();

        synchronized (warnings) {
            for (Map.Entry<Log, List<ScriptWarning>> entry : warnings.entrySet()) {
                Log log = entry.getKey();
                List<ScriptWarning> logWarnings = entry.getValue();
                synchronized (logWarnings) {
                    for (ScriptWarning w : logWarnings) {
                        try {
                            KnownCrashReason reason = new ScriptedAnalysis(log != null ? log.getType() : LogType.LOG, w);
                            KnownCrashReasonMessage message = new KnownCrashReasonMessage(log, reason);
                            message.setProvisional(true);
                            LogAnalyser.replaceCrashReasonMessageIfAnalysisActive(
                                    publishedWarnings.get(w),
                                    message,
                                    () -> publishedWarnings.put(w, message)
                            );
                        } catch (Throwable throwable) {
                            CrashAssistantApp.LOGGER.error("Error while publishing an analysis script result", throwable);
                        }
                    }
                }
            }
        }
    }
}
