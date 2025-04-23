package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.List;
import java.util.Objects;

public class ModernFixWatchDog extends KnownCrashReason {
    public ModernFixWatchDog() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.modernifx_watchdog")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "modernfix"))) return false;
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("IntegratedWatchdog") && line.contains("A single server tick has taken")) {
                if (line.contains("[ModernFix integrated server watchdog/ERROR] ")) {
                    line = line.split("\\[org.embeddedt.modernfix.world.IntegratedWatchdog/]: ")[1];
                }
                message = message.replace("$LINE_FROM_LOG$", line);
                return true;
            }
        }
        return false;
    }
}