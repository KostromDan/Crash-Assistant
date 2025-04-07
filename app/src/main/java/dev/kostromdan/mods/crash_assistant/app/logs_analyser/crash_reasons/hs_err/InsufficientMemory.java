package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.platform.PlatformHelp;

public class InsufficientMemory extends KnownCrashReason {
    public InsufficientMemory() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.insufficient_memory") + getEndRecommendations(),
                "# There is insufficient memory for the Java Runtime Environment to continue\\."
        );
    }

    public static String getEndRecommendations() {
        return PlatformHelp.isLinkDefault() ?
                LanguageProvider.get("warnings.insufficient_memory_indv") :
                LanguageProvider.get("warnings.insufficient_memory_modpacks");
    }
}
