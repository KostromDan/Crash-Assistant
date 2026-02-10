package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

public class ModernIntelDriverIssue extends KnownCrashReason {
    public ModernIntelDriverIssue() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.modern_intel_driver_issue")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isWindows()) return false;
        if (HsErrParser.hsErrContainsOneOfFrames(log, "igxelpicd64.dll")) {
            message = message.replace("$PROBLEMATIC_FRAME$", "igxelpicd64.dll");
        } else if (HsErrParser.hsErrContainsOneOfFrames(log, "igxe2hpgicd64.dll")) {
            message = message.replace("$PROBLEMATIC_FRAME$", "igxe2hpgicd64.dll");
        } else return false;
        return true;
    }
}
