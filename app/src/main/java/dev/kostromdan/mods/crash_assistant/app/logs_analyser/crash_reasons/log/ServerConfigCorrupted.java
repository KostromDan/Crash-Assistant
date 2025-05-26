package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.gui.ControlPanel;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err.InsufficientMemory;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.HashSet;
import java.util.List;

public class ServerConfigCorrupted extends KnownCrashReason {
    public ServerConfigCorrupted() {
        super(
                LogType.CRASH_REPORT,
                LanguageProvider.get("warnings.server_config_corrupted")
        );
    }

    @Override
    public boolean matches(Log log) {
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if( line.contains("ConfigLoadingException: Failed loading config file ")&&
            line.contains(".toml of type SERVER for modid ")){
                String modId = line.split("\\.toml of type SERVER for modid ")[1];
                String config = line.split(": Failed loading config file ")[1].split(" of type SERVER for modid ")[0];

                for (int j = i + 1; j < lines.size(); j++) {
                    if (lines.get(j).contains("Caused by: com.electronwill.nightconfig.core.io.ParsingException: Not enough data available")) {
                        message=message.replace("$MOD_ID$", modId).replace("$CONFIG_FILE$", config);
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }
}