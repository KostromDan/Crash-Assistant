package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalysisUtils;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;

public class InsufficientMemory extends KnownCrashReason {
    public InsufficientMemory() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.insufficient_memory") + getEndRecommendations()
        );
    }

    public static String getEndRecommendations() {
        return PlatformHelp.isLinkDefault() || ModListDiff.isModpackCreator() ?
                LanguageProvider.get("warnings.insufficient_memory_indv", new HashMap<>() {{
                    put("$LINK.MODERN_FIX$", "ModernFix");
                    put("$LINK.FERRITE_CORE$", "FerriteCore");
                }}) :
                LanguageProvider.get("warnings.insufficient_memory_modpacks");
    }

    @Override
    public boolean matches(Log log) {
        return LogAnalysisUtils.hsErrContainsOneOfFrames(log, "# There is insufficient memory for the Java Runtime Environment to continue.");
    }
}
