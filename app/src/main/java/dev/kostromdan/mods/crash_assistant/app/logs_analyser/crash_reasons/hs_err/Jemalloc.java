package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalysisUtils;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

public class Jemalloc extends KnownCrashReason {
    public Jemalloc() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.jemalloc")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isWindows()) return false;
        return LogAnalysisUtils.hsErrContainsOneOfFrames(log, "jemalloc.dll");
    }
}

