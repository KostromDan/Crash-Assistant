package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalysisUtils;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;

public class InsufficientMemory extends KnownCrashReason {
    public InsufficientMemory() {
        super(
                LogType.HS_ERR,
                applyEndRecommendations(LanguageProvider.get("warnings.insufficient_memory"))
        );
    }

    public static String applyEndRecommendations(String message) {
        String endRecommendations = PlatformHelp.isLinkDefault() || ModListDiff.isModpackCreator() ?
                LanguageProvider.get("warnings.insufficient_memory_indv", new HashMap<>() {{
                    put("$LINK.MODERN_FIX$", "ModernFix");
                    put("$LINK.FERRITE_CORE$", "FerriteCore");
                }}) :
                LanguageProvider.get("warnings.insufficient_memory_modpacks");
        return message.replace("$END_RECOMMENDATIONS$", endRecommendations);
    }

    @Override
    public boolean matches(Log log) {
        if (!LogAnalysisUtils.hsErrContainsOneOfFrames(log, "# There is insufficient memory for the Java Runtime Environment to continue."))
            return false;
        String additionalInfo = "";

        String allLines = log.getReader().getAllLinesString();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Memory:.*?physical (\\d+)M.*?\\R.*?TotalPageFile size (\\d+)M", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(allLines);

        if (matcher.find()) {
            int physicalMemory = Integer.parseInt(matcher.group(1));
            int pageFileSize = Integer.parseInt(matcher.group(2));
            if (physicalMemory == pageFileSize) {
                additionalInfo += LanguageProvider.get("warnings.insufficient_memory.page_file_disabled");
            }

            if (!additionalInfo.isEmpty()) {
                additionalInfo = "<span style=\"color:green\">" + additionalInfo + "</span>\n";
            }
            message = message.replace("$FIRST_PRIORITY_WARNINGS$", additionalInfo);
        }

        return true;
    }
}
