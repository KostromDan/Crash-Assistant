package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface TerminatedProcessesFinder {
    static String getTerminatedByWinProcessLogs() {
        synchronized (TerminatedProcessesFinder.class) {
            String fileName = "win_event" + System.currentTimeMillis() + ".txt";
            Path path = Paths.get(fileName);
            String command = "$ErrorActionPreference = 'Continue'; \n" +
                    "Get-WinEvent -FilterHashtable @{ \n" +
                    "  LogName='Application'; \n" +
                    "  Level=2; \n" +
                    "  StartTime=(Get-Date).AddMinutes(-1) \n" +
                    "} *>&1 | Format-Table -Wrap -AutoSize | Out-File \"$FILE_NAME$\" -Encoding UTF8".replace("$FILE_NAME$", fileName);
            try {
                Process process = new ProcessBuilder("powershell.exe", "-Command", command.replaceAll("\\n", ""))
                        .redirectErrorStream(true)
                        .start();

                process.waitFor();
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error wile executing PowerShell command for finding terminated processes.", e);
            }

            try {
                String fileContents = Files.readString(path);
                if (fileContents.contains("NoMatchingEventsFound")) {
                    Files.deleteIfExists(path);
                    return fileName;
                }
                String output = "Detected that Windows has terminated one or more processes within the last minute leading up to the moment of the current Minecraft JVM instance termination,\n" +
                        "so with a very high probability that one of these processes is the Minecraft JVM itself.\n" +
                        "Command used to identify terminated processes:\n \n"
                        + command
                        + "\n \nIf no java.exe (or related JVM processes) are listed below, you can disregard this message.\n" +
                        "To get more information about such errors:\n" +
                        "1) Open Windows Event Viewer (Win+R -> eventvwr.msc -> Enter).\n" +
                        "2) Click \"Windows Logs\" -> \"Application\".\n" +
                        "3) Look for the latest Error you have.\n \n "
                        + fileContents;

                Files.writeString(path, output);
            } catch (Exception ignored) {
            }

            return fileName;
        }
    }
}
