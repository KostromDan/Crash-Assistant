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

public class ModuleResolution extends KnownCrashReason {
    public ModuleResolution() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.LAUNCHER_LOG);
                }},
                LanguageProvider.get("warnings.module_resolution")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        List<String> lastLines = log.getReader().getLastNLines(1000);
        for (int i = lastLines.size() - 1; i >= 0; i--) {
            String line = lastLines.get(i);
            if (line.contains("java.lang.module.ResolutionException") && (line.contains(" exports package ") || line.contains(" export package "))) {
                String errorLine = line.split("java.lang.module.ResolutionException: ")[1];
                String packageName;
                if (line.contains(" exports package ")) {
                    packageName = line.split(" exports package ")[1].split(" ")[0];
                } else {
                    packageName = line.split(" export package ")[1].split(" ")[0];
                }
                List<String> jarsContainingModule = ModuleFinder.findJarsInFolderAsync(Collections.singletonList(packageName), ModListUtils.getCurrentModList(true));

                message = message.replace("$LINE_FROM_LOG$", errorLine);
                message = message.replace("$JARS$", "<strong style='color: red;'>" + String.join("\n", jarsContainingModule) + "</strong>");
                message = message.replace("$PACKAGE$", "<strong>" + packageName + "</strong>");

                return true;
            }
        }

        return false;
    }
}
