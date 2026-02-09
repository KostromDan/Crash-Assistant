package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ModuleResolution extends KnownCrashReason {
    public ModuleResolution() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.STDERR_STREAM);
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
                String additionalInfo = "";
                String packageName;
                if (line.contains(" exports package ")) {
                    packageName = line.split(" exports package ")[1].split(" ")[0];
                } else {
                    packageName = line.split(" export package ")[1].split(" ")[0];
                }
                List<String> jarsContainingModule = ModuleFinder.findJarsInFolderAsync(Collections.singletonList(packageName), ModListUtils.getCurrentModList(true));

                if (PlatformHelp.platform == PlatformHelp.FORGE && (
                        line.contains("and mixinextras.neoforge export package") ||
                                line.contains("Modules mixinextras.neoforge and") ||
                                line.contains("Module mixinextras.neoforge contains package"))) {
                    additionalInfo = "<strong style=\"color:green\">" + LanguageProvider.get("warnings.module_resolution_mixin_extras_neo", new HashMap<String, String>() {{
                        put("$LINK.MIXIN_EXTRAS_NEO$", "Mixin Extras NeoForge on Forge Fix");
                    }}) + "</strong>\n\n";
                }

                message = message.replace("$LINE_FROM_LOG$", errorLine);
                message = message.replace("$JARS$", "<strong style='color: red;'>" + String.join("\n", jarsContainingModule) + "</strong>");
                message = message.replace("$PACKAGE$", "<strong>" + packageName + "</strong>");
                message = message.replace("$FIRST_PRIORITY_WARNINGS$", additionalInfo);

                return true;
            }
        }

        return false;
    }
}
