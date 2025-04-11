package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class LibOpenALDotSo extends KnownCrashReason {
    public LibOpenALDotSo() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.libopenal_so"),
                "# Problematic frame:\\R# C  \\[libopenal\\.so\\+0x[0-9a-fA-F]+\\]"
        );
    }
}
