package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.List;

public class ModernFixWatchDog extends KnownCrashReason {
    public ModernFixWatchDog() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.modernifx_watchdog")
        );
    }

    @Override
    public boolean matches(Log log) {
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