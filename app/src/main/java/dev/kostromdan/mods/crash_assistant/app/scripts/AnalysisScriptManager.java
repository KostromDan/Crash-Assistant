package dev.kostromdan.mods.crash_assistant.app.scripts;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.AbstractScriptManager;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import org.apache.commons.jexl3.JexlContext;
import java.util.List;
import java.util.Map;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AnalysisScriptManager extends AbstractScriptManager {

    private static final AnalysisScriptManager INSTANCE = new AnalysisScriptManager();

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
        JarInJarHelper.setupScripts();
        INSTANCE.runScripts();
        
        Map<Log, List<ScriptWarning>> warnings = Analysis.getRegisteredWarnings();
        
        synchronized (warnings) {
            for (Map.Entry<Log, List<ScriptWarning>> entry : warnings.entrySet()) {
                Log log = entry.getKey();
                for (ScriptWarning w : entry.getValue()) {
                    KnownCrashReason reason = new ScriptedAnalysis(log != null ? log.getType() : LogType.LOG, w);
                    KnownCrashReasonMessage.addCrashReasonMessage(new KnownCrashReasonMessage(log, reason));
                }
            }
        }
    }
}
