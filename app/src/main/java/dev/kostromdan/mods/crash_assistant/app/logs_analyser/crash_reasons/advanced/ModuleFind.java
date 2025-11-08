package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ModuleFind extends KnownCrashReason {
    public ModuleFind() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.LAUNCHER_LOG);
                }},
                LanguageProvider.get("warnings.module_find")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        List<String> lastLines = log.getReader().getLastNLines(1000);
        for (int i = lastLines.size() - 1; i >= 0; i--) {
            String line = lastLines.get(i);
            if (line.contains("java.lang.module.FindException: Module ") && line.contains(" not found, required by ")) {
                String errorLine = line.split("java.lang.module.FindException: ")[1].trim();
                String notFoundModule = line.split("java.lang.module.FindException: Module ")[1].split(" not found, required by ")[0].trim();
                List<String> jarsContainingModule = ModuleFinder.findJarsInFolderAsync(Collections.singletonList(notFoundModule), ModListUtils.getCurrentModList(true));

                message = message.replace("$LINE_FROM_LOG$", errorLine);
                message = message.replace("$JARS$", "<strong style='color: red;'>" + String.join("\n", jarsContainingModule) + "</strong>");

                return true;
            }
        }

        return false;
    }
}
