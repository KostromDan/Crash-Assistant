package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class ErroringEntity extends CodexAnalysis {
    public ErroringEntity() {
        super(LanguageProvider.get("warnings.codex_erroring_entity"),
                "A block in the world is causing problems\\.",
                "The entity '.*?' at the location .*? is causing issues while ticking\\.");
    }
}
