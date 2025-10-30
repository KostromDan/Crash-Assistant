package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MissingUnsupportedDependencies extends KnownCrashReason {
    public MissingUnsupportedDependencies() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.missing_unsupported_dependencies")
        );
        this.conflictingReasons.add("CurseForgeCorrupted");
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (!PlatformHelp.isForgeBased()) return false;
        List<String> lines = log.getReader().getAllLinesList();
        List<String> problemLines = new ArrayList<>();
        HashSet<String> modIds = new HashSet<>();
        List<String> modIdsToJarNames = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Missing or unsupported mandatory dependencies:") ||
                    line.contains("Conflicts between mods:") ||
                    line.contains("Unsupported installed optional dependencies:") ||
                    line.contains("Error during pre-loading phase: Some of your mods are incompatible with the game or each other!") ||
                    line.contains("Incompatibilities between mods:")
            ) {
                if (!line.contains("]: ")) continue;
                if (i + 1 >= lines.size()) continue;
                if (!isMessageLine(lines.get(i + 1))) continue;

                line = line.split("]: ")[1];
                problemLines.add(line);
                for (int j = i + 1; j < lines.size(); j++) {
                    String line2 = lines.get(j);
                    if (!isMessageLine(line2)) {
                        break;
                    }
                    problemLines.add(line2.replaceFirst("^[ \t]+", "&nbsp;&nbsp;&nbsp;&nbsp;"));
                    modIds.addAll(getModIdsFromLine(line2));
                }
            }
        }
        if (problemLines.isEmpty()) return false;
        message = message.replace("$LINE_FROM_LOG$", String.join("\n", problemLines));
        ModListUtils.getCurrentModList(true).stream().forEach(mod -> {
            if (modIds.contains(mod.getModId())) {
                modIdsToJarNames.add(mod.getModId() + " -> " + mod.getJarName());
            }
        });
        message = message.replace("$MOD_IDS_TO_JAR_NAMES$", String.join("\n", modIdsToJarNames));
        return true;
    }

    public static boolean isMessageLine(String line) {
        if (line.isEmpty()) {
            return false;
        }
        if (line.trim().equals("More details:")) return true;
        if (line.trim().equals("A potential solution has been determined, this may resolve your problem:")) return true;
        char firstChar = line.charAt(0);
        if (firstChar != ' ' && firstChar != '\t') {
            return false;
        }
        if (line.contains("Issues may arise. Continue at your own risk.")) {
            return false;
        }
        return true;
    }


    public static List<String> getModIdsFromLine(String line) {
        List<String> modIds = new ArrayList<>();
        String[] splitLine = line.split("'");
        for (int i = 0; i < splitLine.length; i++) {
            String currentLine = splitLine[i];
            if ((currentLine.endsWith("Mod ID: ") ||
                    currentLine.endsWith("Requested by: ") ||
                    currentLine.endsWith("Mod ") ||
                    currentLine.endsWith("discourages ")) && i + 1 < splitLine.length) {
                modIds.add(splitLine[i + 1]);
            }
        }
        return modIds;
    }
}