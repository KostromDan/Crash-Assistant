package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;

import java.util.*;

public class LauncherLogReader extends LogReader {
    private static final List<String> MinecraftLauncherLogNames = Arrays.asList(
            "MinecraftLauncher: launcher_log.txt",
            "CurseForge: launcher_log.txt"
    );
    private Integer exitCode = null;

    public Integer getExitCode() {
        return exitCode;
    }

    public LauncherLogReader(Log log) {
        super(log);
    }

    @Override
    public synchronized List<String> getAllLinesList() {
        synchronized (this) {
            if (allLinesListCached == null) {
                allLinesListCached = new ArrayList<>();
                allLinesListCached.addAll(firstLines);
                if (lastLines != null) allLinesListCached.addAll(lastLines);
                cutLauncherLog();
            }
            return allLinesListCached;
        }
    }

    private void cutLauncherLog() {
        if (MinecraftLauncherLogNames.contains(log.getName())) {
            cutVanillaLauncherLog();
            return;
        } else if (Objects.equals(log.getName(),"launcher_log.txt")) {
            return;
        }
        allLinesListCached = Collections.singletonList("");
    }

    private void cutVanillaLauncherLog() {
        int launchedLine = -1;
        Integer launchedGameId = null;
        int stoppedLine = -1;
        Integer stoppedGameId = null;
        long lastLineEpochMillis = -1;
        for (int i = allLinesListCached.size() - 1; i >= 0; i--) {
            String line = allLinesListCached.get(i);
            if (lastLineEpochMillis == -1 && line.contains("MinecraftJavaLoggingContext")) {
                Long parsedMillis = getEpochMillisFromMinecraftLauncherLog(line);
                lastLineEpochMillis = parsedMillis == null ? -2 : parsedMillis;
            }
            if (stoppedLine == -1 && line.contains("Game closed with id ")) {
                stoppedLine = i;
                stoppedGameId = Integer.parseInt(line.split("Game closed with id ")[1]);
                if (i - 1 >= 0) {
                    for (int j = i - 1; j >= 0; j--) {
                        String exitCodeStr = allLinesListCached.get(i - 1);
                        if (exitCodeStr.contains("Launcher/monitor (Process Monitor)") && exitCodeStr.contains("with exit code ")) {
                            exitCode = Integer.parseInt(exitCodeStr.split("with exit code ")[1]);
                            break;
                        }
                    }

                }
            } else if (launchedLine == -1 && line.contains("Starting game with token ")) {
                launchedLine = i;
                launchedGameId = Integer.parseInt(line.split("Starting game with token ")[1]);
                break;
            }
        }

        if (lastLineEpochMillis > 0 && Boot.parentStarted > lastLineEpochMillis) {
            allLinesListCached = Collections.singletonList("");
            return;
        }

        if (launchedGameId != null && launchedGameId.equals(stoppedGameId) && stoppedLine > launchedLine) {
            allLinesListCached = allLinesListCached.subList(launchedLine, stoppedLine + 1);
            return;
        }
        if (lastLines == null) {
            if (launchedLine != -1 && stoppedLine == -1) {
                allLinesListCached = allLinesListCached.subList(launchedLine, allLinesListCached.size());
                return;
            }
        } else {
            if (stoppedLine != -1 && launchedLine != -1) {
                int launchedLineIndexInLastLines = lastLines.lastIndexOf(allLinesListCached.get(launchedLine));
                int stoppedLineIndexInLastLines = lastLines.lastIndexOf(allLinesListCached.get(stoppedLine));
                if (launchedLineIndexInLastLines == -1 || stoppedLineIndexInLastLines != -1) {
                    allLinesListCached = lastLines.subList(0, stoppedLineIndexInLastLines+1);
                    return;
                }
            }
        }
        allLinesListCached = Collections.singletonList("");
    }

    public static Long getEpochMillisFromMinecraftLauncherLog(String logLine) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\[Info: (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.(\\d+)):")
                .matcher(logLine);

        if (matcher.find()) {
            try {
                String dateTimePart = matcher.group(1);
                String fractionalPart = matcher.group(2);

                if (fractionalPart.length() > 6) {
                    fractionalPart = fractionalPart.substring(0, 6);
                } else {
                    fractionalPart = String.format("%-6s", fractionalPart).replace(' ', '0');
                }

                String normalizedDateTime = dateTimePart.substring(0, dateTimePart.lastIndexOf('.') + 1) + fractionalPart;

                java.time.ZonedDateTime utcDateTime = java.time.LocalDateTime.parse(
                        normalizedDateTime,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
                ).atZone(java.time.ZoneOffset.UTC);

                return utcDateTime.withZoneSameInstant(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
