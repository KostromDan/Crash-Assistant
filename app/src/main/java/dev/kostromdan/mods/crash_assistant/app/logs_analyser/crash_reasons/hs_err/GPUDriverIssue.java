package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;


public class GPUDriverIssue extends KnownCrashReason {
    public GPUDriverIssue() {
        super(
                LogType.HS_ERR,
                formatGPUDriverIssueMsg(LanguageProvider.get("warnings.gpu_driver_issue"))
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isWindows()) return false;
        if (HsErrParser.hsErrContainsOneOfFrames(log, "glfw.dll")) {
            message = message.replace("$PROBLEMATIC_FRAME$", "glfw.dll");
            return true;
        }
        return false;
    }

    public static String formatGPUDriverIssueMsg(String originalMessage) {
        return originalMessage.replace("$CURRENT_GPU$", CrashAssistantApp.renderer != null ? CrashAssistantApp.renderer : "UNDEFINED");
    }
}
