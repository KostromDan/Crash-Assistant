package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

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
        if (PlatformHelp.platform != PlatformHelp.FORGE) return false;
        List<String> lines = log.getReader().getAllLinesList();
        String modsLine = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Found duplicate mods:") && modsLine == null) {
                if (i + 1 < lines.size()) {
                    modsLine = lines.get(i + 1);
                }
            }
            if (modsLine != null && line.contains("EarlyLoadingException: Duplicate mods found")) {
                message = message.replace("$MODS_LINE$", modsLine);
                return true;
            }
        }
        return false;
    }
}