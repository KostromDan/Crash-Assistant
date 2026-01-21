package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashSet;

public class MedievalOriginsVsForgeOrigins extends KnownCrashReason {
    public MedievalOriginsVsForgeOrigins() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.STDERR_STREAM);
                }},
                LanguageProvider.get("warnings.medieval_origins"),
                "Caused by: java\\.lang\\.ClassCastException: class net\\.minecraft\\.world\\.item\\.ItemStack cannot be cast to class io\\.github\\.apace100\\.apoli\\.access\\.EntityLinkedItemStack \\(net\\.minecraft\\.world\\.item\\.ItemStack is in module"
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (PlatformHelp.platform != PlatformHelp.FORGE) return false;
        return super.matches(log);
    }
}