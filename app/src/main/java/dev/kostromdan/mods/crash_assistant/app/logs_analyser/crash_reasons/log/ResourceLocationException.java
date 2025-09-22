package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.HashSet;
import java.util.List;

public class ResourceLocationException extends KnownCrashReason {
    public ResourceLocationException() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.resource_location_exception")
        );
    }

    @Override
    public boolean matches(Log log) {
        List<String> lines = log.getReader().getAllLinesList();
        boolean prevLineIsFailureMessage = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Caused by: net.minecraft.ResourceLocationException: Non [a-z0-9/._-] character in path of location: ") ||
                    (prevLineIsFailureMessage && line.contains("net.minecraft.ResourceLocationException: Non [a-z0-9/._-] character in path of location: "))) {
                if (!prevLineIsFailureMessage) {
                    line = line.split("ResourceLocationException: ")[1];
                } else {
                    line = lines.get(i - 1) + "\n" + line;
                }
                message = message.replace("$LINE_FROM_LOG$", line);
                return true;
            }
            prevLineIsFailureMessage = line.contains("Failure message: ");
        }
        return false;
    }
}
