package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.util.HashSet;
import java.util.List;

public class KnownCrashReason {
    protected HashSet<LogType> logTypes;
    protected List<String> patterns;
    protected String message;
    public static HashSet<KnownCrashReason> shownKnownCrashReasons = new HashSet<>();

    protected KnownCrashReason(LogType logType, String message, List<String> patterns) {
        this.logTypes = new HashSet<>() {{
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
        this.logTypes = new HashSet<>() {{
            add(logType);
        }};
        this.message = message;
        this.patterns = List.of(patterns);
    }

    protected KnownCrashReason(HashSet<LogType> logTypes, String message, String... patterns) {
        this.logTypes = logTypes;
        this.message = message;
        this.patterns = List.of(patterns);
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

    public boolean matches(Log log) {
        return RegexChecker.logContainsOneOfPatterns(log, patterns);
    }
}
