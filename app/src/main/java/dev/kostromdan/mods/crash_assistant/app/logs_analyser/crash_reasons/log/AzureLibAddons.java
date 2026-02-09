package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.AzureLibDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.JdepsDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import java.util.HashSet;
import java.util.Objects;


public class AzureLibAddons extends KnownCrashReason {
    public AzureLibAddons() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                    add(LogType.STDERR_STREAM);
                }},
                LanguageProvider.get("warnings.azure_lib_addons"),
                "(?i)java\\.lang\\.(ClassNotFoundException|NoClassDefFoundError): mod[./]azure[./]azurelib"
        );
        autoFixButtons.put(LanguageProvider.get("gui.analysis.find_incompatible_azure_lib_addons"), (dialog) -> AzureLibDependenciesAnalysisGUI.showAzureLibAnalysisDialog((JFrame) dialog.getOwner()));
        autoFixButtons.put(LanguageProvider.get("gui.analysis.azure_lib.find_mods"), (dialog) -> {
            new JdepsDependenciesAnalysisGUI((JFrame) dialog.getOwner(), "mod.azure.azurelib").start();
        });
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "azurelib"))) {
            return false;
        }
        ModListDiff diff = ModListDiff.getDiff(true);
        if (!PlatformHelp.isLinkDefault() && diff.getAddedMods().isEmpty() && diff.getUpdatedMods().isEmpty()) {
            return false;
        }
        return super.matches(log);
    }


}