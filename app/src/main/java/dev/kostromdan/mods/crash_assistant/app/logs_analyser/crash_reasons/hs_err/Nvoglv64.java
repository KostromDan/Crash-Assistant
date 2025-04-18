package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class Nvoglv64 extends KnownCrashReason {
    public Nvoglv64() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.nvoglv64"),
                "# Problematic frame:\\R# C  \\[nvoglv64\\.dll\\+0x[0-9A-Fa-f]+\\]"
        );
    }
}
