package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.List;

public class ResourceLocationException extends KnownCrashReason {
    public ResourceLocationException() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.resource_location_exception")
        );
    }

    @Override
    public boolean matches(Log log) {
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Caused by: net.minecraft.ResourceLocationException: Non [a-z0-9/._-] character in path of location: ")) {
                line = line.split("ResourceLocationException: ")[1];
                message = message.replace("$LINE_FROM_LOG$", line);
                return true;
            }
        }
        return false;
    }
}
