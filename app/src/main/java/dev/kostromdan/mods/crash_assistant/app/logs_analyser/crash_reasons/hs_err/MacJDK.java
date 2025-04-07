package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import java.util.HashMap;

public class MacJDK extends KnownCrashReason {
    public MacJDK() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.macjdk", new HashMap<>() {{
                    put("$LINK.AZUL_DOWNLOAD$", LanguageProvider.get("warnings_common.here"));
                    put("$LINK.ATL$", "ATLauncher");
                }}),
                "# Problematic frame:\\R# v  ~StubRoutines::SafeFetch32"
        );
    }
}
