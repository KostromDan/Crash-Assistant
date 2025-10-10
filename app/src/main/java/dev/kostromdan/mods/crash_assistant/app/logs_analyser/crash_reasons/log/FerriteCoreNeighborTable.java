package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.List;

public class FerriteCoreNeighborTable extends KnownCrashReason {
    public FerriteCoreNeighborTable() {
        super(
                LogType.LAUNCHER_LOG,
                LanguageProvider.get("warnings.ferritecore_neighbor_table")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        List<String> lastLines = log.getReader().getLastNLines(300);
        for (int i = lastLines.size() - 1; i >= 0; i--) {
            String line = lastLines.get(i);
            if (line.contains("Caused by: java.lang.UnsupportedOperationException: A mod tried to access the state neighbor table directly. Please report this at https://github.com/malte0811/FerriteCore/issues. As a temporary workaround you can enable \"populateNeighborTable\" in the FerriteCore config")) {
                String errorLine = line.split("Caused by: java.lang.UnsupportedOperationException: ")[1];
                errorLine = errorLine.replace(". As a temporary", ".\nAs a temporary");
                message = message.replace("$ERROR_LINE$", errorLine);
                return true;
            }
        }
        return false;
    }
}
