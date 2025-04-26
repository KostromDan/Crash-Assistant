package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalysisUtils;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;

public class Jvm extends KnownCrashReason {
    public Jvm() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.jvm", new HashMap<String, String>() {{
                            put("$LINK.RESULTS_OF_MEMORY_DIAGNOSTICS$", LanguageProvider.get("warnings_common.instruction"));
                        }})
                        .replace("$HALF_OF_PROCESSORS$",
                                String.valueOf(Runtime.getRuntime().availableProcessors() / 2))
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isWindows()) return false;
        return LogAnalysisUtils.hsErrContainsOneOfFrames(log, "jvm.dll");
    }
}
