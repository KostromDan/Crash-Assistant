package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalysisUtils;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;


public class nglMultiDrawElementsBaseVertex extends KnownCrashReason {
    public nglMultiDrawElementsBaseVertex() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.0x0000").replace("$CURRENT_GPU$", CrashAssistantApp.renderer != null ? CrashAssistantApp.renderer : "UNDEFINED"),
                "Java frames:.*\\R.*org\\.lwjgl\\.opengl\\.GL32C\\.nglMultiDrawElementsBaseVertex\\(IJIJIJ\\)V.*"
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isWindows()) return false;
        if (!LogAnalysisUtils.hsErrContainsOneOfFrames(log, "0x0000")) return false;
        if (!LogAnalysisUtils.problematicFrame.get().startsWith("# C  0x0")) return false;
        return super.matches(log);
    }
}
