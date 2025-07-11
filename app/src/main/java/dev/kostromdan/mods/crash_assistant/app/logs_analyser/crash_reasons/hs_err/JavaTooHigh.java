package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.VersionUtils;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

public class JavaTooHigh extends KnownCrashReason {
    public JavaTooHigh() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.java_too_high").replace("$JAVA_VERSION$", PlatformHelp.javaVersion)
        );
    }

    @Override
    public boolean matches(Log log) {
        if (PlatformHelp.isWindows()) return false;
        if (!VersionUtils.isGreaterThanOrEqual(PlatformHelp.javaVersion, "22.0.0")) return false;
        return HsErrParser.hsErrContainsAllOfFrames(log, "spark-", "libasyncProfiler.so.tmp");
    }
}
