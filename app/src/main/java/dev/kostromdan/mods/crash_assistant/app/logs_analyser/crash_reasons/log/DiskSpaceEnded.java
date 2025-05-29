package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.HashSet;

public class DiskSpaceEnded extends KnownCrashReason {
    public DiskSpaceEnded() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.disk_space_ended"),
                "java\\.io\\.IOException: There is not enough space on the disk"
        );
    }
}
