package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

public class DuplicatedMods extends KnownCrashReason {
    public DuplicatedMods() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.duplicated_mods"),
                ".*Failed to build unique mod list after mod discovery\\.\\R"
                        + "net\\.minecraftforge\\.fml\\.loading\\.EarlyLoadingException: Duplicate mods found"

        );
    }

    @Override
    public boolean matches(String logText, Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (PlatformHelp.platform != PlatformHelp.FORGE) return false;
        return super.matches(logText, log);

    }
}