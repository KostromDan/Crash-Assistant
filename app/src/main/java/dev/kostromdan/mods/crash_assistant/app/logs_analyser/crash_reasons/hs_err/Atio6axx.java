package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class Atio6axx extends KnownCrashReason {
    public Atio6axx() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.atio6axx"),
                "# Problematic frame:\\R# C  \\[atio6axx\\.dll\\+0x[0-9a-fA-F]+\\]"
        );
    }
}
