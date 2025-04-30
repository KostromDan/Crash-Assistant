package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class AlLibAlcCleanup extends KnownCrashReason {
    public AlLibAlcCleanup() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.alc_cleanup"),
                "AL lib: \\(EE\\) alc_cleanup: 1 device not closed"
        );
        this.priority = 2000;
    }
}
