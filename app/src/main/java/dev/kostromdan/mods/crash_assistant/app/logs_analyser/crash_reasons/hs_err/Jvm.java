package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.HashMap;

public class Jvm extends KnownCrashReason {
    public Jvm() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.jvm")
                        .replace("$HALF_OF_PROCESSORS$",
                                String.valueOf(Runtime.getRuntime().availableProcessors() / 2)),
                "# Problematic frame:\\R# V  \\[jvm\\.dll\\+0x[0-9a-fA-F]+\\]"
        );
    }
}
