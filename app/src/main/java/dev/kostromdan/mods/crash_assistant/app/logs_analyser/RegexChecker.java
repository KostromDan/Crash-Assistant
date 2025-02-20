package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;

public class RegexChecker {
    public static boolean logContainsOneOfPatterns(Path logFile, String... patterns) {
        if (patterns.length == 0 || !logFile.toFile().isFile()) {
            return false;
        }
        try {
            String logContents = Files.readString(logFile);
            String combinedPattern = String.join("|", patterns);
            return Pattern.compile(combinedPattern).matcher(logContents).find();
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing " + logFile.getFileName().toString() + " file: ", e);
            return false;
        }
    }
    public static boolean logContainsOneOfPatterns(Path logFile, Collection<String> patterns) {
        if (patterns == null) {
            return false;
        }
        return logContainsOneOfPatterns(logFile, patterns.toArray(new String[0]));
    }
}