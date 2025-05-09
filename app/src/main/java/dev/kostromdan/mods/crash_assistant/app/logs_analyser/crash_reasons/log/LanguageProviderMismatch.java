package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err.InsufficientMemory;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashSet;
import java.util.List;

public class LanguageProviderMismatch extends KnownCrashReason {
    public LanguageProviderMismatch() {
        super(
                new HashSet<>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.language_provider_mismatch")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (!PlatformHelp.isForgeBased()) return false;
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Mod File ") && line.contains(" needs language provider")) {
                String errorLine = line;
                if (i + 1 < lines.size() && lines.get(i + 1).contains("We have found")) {
                    errorLine += "\n" + lines.get(i + 1);
                }
                message = message.replace("$LINE_FROM_LOG$", errorLine);
                return true;
            }
        }
        return false;
    }
}