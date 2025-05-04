package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

public class Nvoglv64 extends KnownCrashReason {
    public Nvoglv64() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.nvoglv64")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isWindows()) return false;
        return HsErrParser.hsErrContainsOneOfFrames(log, "nvoglv64.dll");
    }
}
