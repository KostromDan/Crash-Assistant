package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err.InsufficientMemory;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import java.util.HashSet;

public class OutOfMemoryError extends KnownCrashReason {
    public OutOfMemoryError() {
        super(
                new HashSet<>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.out_of_memory_error") + InsufficientMemory.getEndRecommendations(),
                "java\\.lang\\.OutOfMemoryError"
        );
    }
}