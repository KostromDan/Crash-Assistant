package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.List;

public class McdaMcdwVsClumps extends KnownCrashReason {
    public McdaMcdwVsClumps() {
        super(
                LogType.CRASH_REPORT,
                LanguageProvider.get("warnings.mcda_mcdw_vs_clumps")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (PlatformHelp.platform != PlatformHelp.FORGE) return false;

        String lines = log.getReader().getAllLinesString();

        if (lines.contains("Could not execute entrypoint stage 'main' due to errors, provided by 'mcd") &&
                lines.contains("Caused by: java.lang.ClassNotFoundException: com.blamejared.clumps.api.")) {
            return true;
        }
        return false;
    }
}