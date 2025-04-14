package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class Jemalloc extends KnownCrashReason {
    public Jemalloc() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.jemalloc"),
                "# Problematic frame:\\R# C  \\[jemalloc\\.dll\\+0x[0-9a-fA-F]+\\]"
        );
    }
}

