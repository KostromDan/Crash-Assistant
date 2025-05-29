package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParsingResult;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;
import java.util.Optional;

public class InsufficientMemory extends KnownCrashReason {
    public InsufficientMemory() {
        super(
                LogType.HS_ERR,
                applyEndRecommendations(LanguageProvider.get("warnings.insufficient_memory"))
        );
    }

    public static String applyEndRecommendations(String message) {
        String endRecommendations = PlatformHelp.isLinkDefault() || ModListDiff.isModpackCreator() ?
                LanguageProvider.get("warnings.insufficient_memory_indv", new HashMap<String, String>() {{
                    put("$LINK.MODERN_FIX$", "ModernFix");
                    put("$LINK.FERRITE_CORE$", "FerriteCore");
                }}) :
                LanguageProvider.get("warnings.insufficient_memory_modpacks");
        return message.replace("$END_RECOMMENDATIONS$", endRecommendations);
    }

    @Override
    public boolean matches(Log log) {
        if (!HsErrParser.hsErrContainsOneOfFrames(log, "# There is insufficient memory for the Java Runtime Environment to continue."))
            return false;
        String additionalInfo = "";

        Optional<HsErrParsingResult> hsErrParsingResult = HsErrParser.parseHsErr(log);
        if (hsErrParsingResult.isPresent() && hsErrParsingResult.get().isPageFileDisabled()) {
            additionalInfo += LanguageProvider.get("warnings.insufficient_memory.page_file_disabled");
        }

        if (!additionalInfo.isEmpty()) {
            additionalInfo = "<span style=\"color:green\">" + additionalInfo + "</span>\n";
        }

        message = message.replace("$FIRST_PRIORITY_WARNINGS$", additionalInfo);


        return true;
    }
}
