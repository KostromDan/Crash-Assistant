package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.ArrayList;
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
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Missing or unsupported mandatory dependencies:")) {
                if(line.contains("]: ")) {
                    line = line.split("]: ")[1];
                }
                problemLines.add(line);
                for (int j = i + 1; j < lines.size(); j++) {
                    String line2 = lines.get(j);
                    if (line2.isEmpty() || !(line2.charAt(0) == ' ' || line2.charAt(0) == '\t')) {
                        break;
                    }
                    problemLines.add(line2.replaceFirst("^[ \t]+", "&nbsp;&nbsp;&nbsp;&nbsp;"));
                }
                message = message.replace("$LINE_FROM_LOG$", String.join("\n", problemLines));
                return true;
            }
        }
        return false;
    }
}