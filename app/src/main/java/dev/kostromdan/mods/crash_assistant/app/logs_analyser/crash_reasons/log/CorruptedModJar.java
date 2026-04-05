package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.CorruptedJarFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.FileUtils;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class CorruptedModJar extends KnownCrashReason {
    public CorruptedModJar() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                    add(LogType.STDERR_STREAM);
                }},
                LanguageProvider.get("warnings.corrupted_mod_jar")
        );
        autoFixButtons.put(LanguageProvider.get("gui.analysis.find_corrupted_mod_jars"), (dialog) -> CorruptedJarFinderGUI.showDialog((JFrame) dialog.getOwner()));
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (!PlatformHelp.isForgeBased()) return false;
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.contains("zip")) {
                continue;
            }
            if (!line.contains("zip END header not found") && !line.contains("zip file is empty")) {
                continue;
            }
            List<String> linesToSearch = lines.subList(Math.max(0, i - 1), Math.min(lines.size(), i + 2));
            boolean found = false;
            for (String lineToSearch : linesToSearch) {
                if (lineToSearch.contains("UnionFileSystem$UncheckedIOException")) found = true;
                if (lineToSearch.contains("Error during early discovery")) found = true;
                if (lineToSearch.contains("File ") && lineToSearch.contains(" is not a jar file")) found = true;
                if (lineToSearch.contains("Failed to create secure jar for")) found = true;
            }
            if (found) {
                if (FileUtils.isCurseForgeEnv() && hasMinecraftLocatorStackTrace(lines)) {
                    message = LanguageProvider.get("warnings.curseforge_corrupted", new HashMap<String, String>() {{
                        put("$LINK.ATL$", "ATLauncher");
                    }});
                    autoFixButtons.clear();
                }
                return true;
            }
        }
        return false;
    }

    public static boolean hasMinecraftLocatorStackTrace(List<String> lines) {
        for (String line : lines) {
            if (!line.contains("at ")) continue;
            if (!line.contains("net.minecraftforge.fml.loading.moddiscovery.MinecraftLocator.")) continue;
            return true;
        }
        return false;
    }
}