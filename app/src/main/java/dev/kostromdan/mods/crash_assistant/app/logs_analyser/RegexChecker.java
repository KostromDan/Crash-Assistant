package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;

public class RegexChecker {
    public static boolean logContainsOneOfPatterns(Log log, Collection<String> patterns) {
        if (patterns == null) {
            return false;
        }
        return logContainsOneOfPatterns(log, patterns.toArray(new String[0]));
    }

    public static boolean logContainsOneOfPatterns(Log log, String... patterns) {
        if (patterns.length == 0 || !log.getFile().isFile()) {
            return false;
        }
        try {
            return logContainsOneOfPatterns(log.getReader().getAllLinesString(), log.getPath(), patterns);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while reading " + log.getFileName() + " file: ", e);
            return false;
        }
    }

    public static boolean logContainsOneOfPatterns(String logContents, Path logFile, Collection<String> patterns) {
        if (patterns == null) {
            return false;
        }
        return logContainsOneOfPatterns(logContents, logFile, patterns.toArray(new String[0]));
    }

    public static boolean logContainsOneOfPatterns(String logContents, Path logFile, String... patterns) {
        if (patterns.length == 0) {
            return false;
        }
        try {
            String combinedPattern = String.join("|", patterns);
            return Pattern.compile(combinedPattern).matcher(logContents).find();
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing " + logFile.getFileName().toString() + " file: ", e);
            return false;
        }
    }
}