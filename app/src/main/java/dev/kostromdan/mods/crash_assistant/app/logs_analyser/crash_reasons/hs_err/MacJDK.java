package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;

public class MacJDK extends KnownCrashReason {
    public MacJDK() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.macjdk", new HashMap<String, String>() {{
                    put("$LINK.AZUL_DOWNLOAD$", LanguageProvider.get("warnings_common.here"));
                    put("$LINK.ATL$", "ATLauncher");
                }})
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isMacOS()) return false;
        return HsErrParser.hsErrContainsOneOfFrames(log, "~StubRoutines::SafeFetch32");
    }
}
