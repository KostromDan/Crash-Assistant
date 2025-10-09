package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class KnownCrashReason {
    protected HashSet<LogType> logTypes;
    protected List<String> patterns;
    protected String message;
    protected int priority;
    protected HashSet<String> conflictingReasons = new HashSet<>();
    public static HashSet<KnownCrashReason> shownKnownCrashReasons = new HashSet<>();

    protected KnownCrashReason(LogType logType, String message, List<String> patterns) {
        this.logTypes = new HashSet<LogType>() {{
            add(logType);
        }};
        this.message = message;
        this.patterns = patterns;
    }

    protected KnownCrashReason(HashSet<LogType> logTypes, String message, List<String> patterns) {
        this.logTypes = logTypes;
        this.message = message;
        this.patterns = patterns;
    }

    protected KnownCrashReason(LogType logType, String message, String... patterns) {
        this.logTypes = new HashSet<LogType>() {{
            add(logType);
        }};
        this.message = message;
        this.patterns = Arrays.asList(patterns);
    }

    protected KnownCrashReason(HashSet<LogType> logTypes, String message, String... patterns) {
        this.logTypes = logTypes;
        this.message = message;
        this.patterns = Arrays.asList(patterns);
    }

    HashSet<LogType> getLogTypes() {
        return logTypes;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public String getMessage() {
        return message;
    }

    public int getPriority() {
        return priority;
    }

    public HashSet<String> getConflictingReasons() {
        return conflictingReasons;
    }

    public String getAutoFixButtonText() {
        return null;
    }

    public Consumer<JDialog> getAutoFixButtonAction() {
        return null;
    }

    public boolean matches(Log log) {
        return RegexChecker.logContainsOneOfPatterns(log, patterns);
    }
}
