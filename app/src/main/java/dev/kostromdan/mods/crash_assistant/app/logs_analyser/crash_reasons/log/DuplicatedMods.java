package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.List;

public class DuplicatedMods extends KnownCrashReason {
    public DuplicatedMods() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.duplicated_mods")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
//        if (PlatformHelp.platform != PlatformHelp.FORGE) return false;
        List<String> lines = log.getProcessor().getAllLinesList();
        boolean found = false;
        boolean exception = false;
        String modsLine = null;
        for (String line : lines) {
            if (found && modsLine == null) {
                modsLine = line;
                continue;
            }
            if (line.contains("Found duplicate mods:")) {
                found = true;
                continue;
            }
            if (found && line.contains("EarlyLoadingException: Duplicate mods found")) {
                exception = true;
                break;
            }
        }
        if (modsLine == null || !exception) {
            return false;
        }
        message = message.replace("$MODS_LINE$", modsLine);
        return true;
    }
}