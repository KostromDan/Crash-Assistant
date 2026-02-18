package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public class KnownCrashReason {
    protected HashSet<LogType> logTypes;
    protected List<String> patterns;
    protected String message;
    protected int priority;
    protected String dontShowAgainKey = null;
    protected int customOkDelay = 0;
    protected HashSet<String> conflictingReasons = new HashSet<>();
    public static HashSet<KnownCrashReason> shownKnownCrashReasons = new HashSet<>();

    protected LinkedHashMap<String, Consumer<JDialog>> autoFixButtons = new LinkedHashMap<>();

    public KnownCrashReason(LogType logType, String message, List<String> patterns) {
        this.logTypes = new HashSet<LogType>() {{
            add(logType);
        }};
        this.message = message;
        this.patterns = patterns;
    }

    public KnownCrashReason(HashSet<LogType> logTypes, String message, List<String> patterns) {
        this.logTypes = logTypes;
        this.message = message;
        this.patterns = patterns;
    }

    public KnownCrashReason(LogType logType, String message, String... patterns) {
        this.logTypes = new HashSet<LogType>() {{
            add(logType);
        }};
        this.message = message;
        this.patterns = Arrays.asList(patterns);
    }

    public KnownCrashReason(HashSet<LogType> logTypes, String message, String... patterns) {
        this.logTypes = logTypes;
        this.message = message;
        this.patterns = Arrays.asList(patterns);
    }

    public String getReasonName() {
        return getClass().getSimpleName();
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

    public LinkedHashMap<String, Consumer<JDialog>> getAutoFixButtons() {
        return autoFixButtons;
    }

    public boolean matches(Log log) {
        return RegexChecker.logContainsOneOfPatterns(log, patterns);
    }

    public String getDontShowAgainKey() {
        return dontShowAgainKey;
    }

    public void setDontShowAgainKey(String dontShowAgainKey) {
        this.dontShowAgainKey = dontShowAgainKey;
    }

    public int getOkDelay() {
        return customOkDelay;
    }

    public void setOkDelay(int okDelay) {
        this.customOkDelay = okDelay;
    }
}
