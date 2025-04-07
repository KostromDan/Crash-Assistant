package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.RegexChecker;

import java.nio.file.Paths;
import java.util.HashSet;

public class CodexAnalysis extends KnownCrashReason {
    protected CodexAnalysis(String message, String... patterns) {
        super(new HashSet<>(), message, patterns);
    }

    public boolean matches(String codexMessage) {
        return RegexChecker.logContainsOneOfPatterns(codexMessage, Paths.get(""), patterns);
    }
}
