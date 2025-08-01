package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TerminatedProcessesFinder {
    public static String getTerminatedByWinProcessLogs() {
        String fileName = "win_event" + System.currentTimeMillis() + ".txt";
        Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), fileName);
        Path targetPath = Paths.get(fileName);
        String command = "$ErrorActionPreference = 'Continue'; \n" +
                "Get-WinEvent -FilterHashtable @{ \n" +
                "  LogName='Application'; \n" +
                "  Level=2; \n" +
                "  StartTime=((Get-Date).AddSeconds(-15).ToString('o')) \n" +
                "} *>&1 | Format-Table -Wrap -AutoSize | Out-File \"$FILE_NAME$\" -Encoding UTF8".replace("$FILE_NAME$", tempPath.toString());
        try {
            Process process = new ProcessBuilder("powershell.exe", "-Command", command.replaceAll("\\n", ""))
                    .redirectErrorStream(true)
                    .start();

            process.waitFor();
        } catch (Exception e) {
            if (e.getMessage().contains("Cannot run program \"powershell.exe\":")) {
                return fileName;
            }
            CrashAssistantApp.LOGGER.error("Error wile executing PowerShell command for finding terminated processes.", e);
        }

        try {
            String fileContents = new String(Files.readAllBytes(tempPath), StandardCharsets.UTF_8);
            if (fileContents.contains("NoMatchingEventsFound") || fileContents.length() <= 8) {
                Files.deleteIfExists(tempPath);
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

            Files.write(targetPath, output.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }

        return fileName;
    }
}
