package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.kostromdan.mods.crash_assistant.app.utils.TerminatedProcessesFinder.removeSpacesAndEndLines;
import static dev.kostromdan.mods.crash_assistant.app.utils.TerminatedProcessesFinder.wasClosedMessagesRegex;

public class RegexTest {

    public static void main(String[] args) {
        String logFilePath = "app/src/main/java/dev/kostromdan/mods/crash_assistant/app/logs_analyser/win_event_test.txt";

        String logContent;
        try {
            logContent = removeSpacesAndEndLines(Files.readString(Paths.get(logFilePath)));
            System.out.println(logContent);
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            return;
        }

        for (int i = 0; i < wasClosedMessagesRegex.size(); i++) {
            String regex = wasClosedMessagesRegex.get(i);
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(logContent);
            if (matcher.find()) {
                System.out.println("Found " + i);
            } else {
                System.out.println("Not found " + i);
            }
        }
    }
}