package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import org.apache.commons.jexl3.annotations.NoJexl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class LogsList {
    private static final Set<Log> logs = Collections.synchronizedSet(new TreeSet<>(new LogComparator()));

    public static Set<Log> getLogs() {
        return logs;
    }

    @NoJexl
    public static List<Log> getLogsSnapshot() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public static boolean isLauncherLogExist() {
        return getLogsSnapshot().stream().anyMatch(log -> log.getType() == LogType.LAUNCHER_LOG);
    }

    public static List<Log> getLogs(List<LogType> types) {
        return getLogsSnapshot().stream()
                .filter(log -> types.contains(log.getType()))
                .collect(Collectors.toList());
    }

    public static List<Log> getLogs(LogType... types) {
        return getLogs(Arrays.asList(types));
    }

    @NoJexl
    public static void addIfExistsAndModified(Log log) {
        addIfExistsAndModified(log, true, true);
    }

    @NoJexl
    public static void addIfExistsAndModified(Log log, boolean checkModified, boolean checkSize) {
        if (Files.exists(log.getPath()) && Files.isRegularFile(log.getPath())) {
            if (CrashAssistantConfig.getBlacklistedLogs().stream().anyMatch(
                    bl_log -> log.getFileName().startsWith(bl_log))) {
                return;
            }
            if (checkModified && log.getPath().toFile().lastModified() <= Boot.parentStarted) {
                return;
            }
            try {
                if (checkSize && Files.size(log.getPath()) == 0) {
                    CrashAssistantApp.LOGGER.warn("File \"" + log.getPath() + "\" is empty.");
                    return;
                }
            } catch (IOException e) {
                CrashAssistantApp.LOGGER.error("Error while checking file size \"" + log.getPath() + "\": ", e);
            }
            Path newPath = log.getPath().toAbsolutePath().normalize();
            synchronized (logs) {
                for (Log existingLog : logs) {
                    if (existingLog.getPath().toAbsolutePath().normalize().equals(newPath)) {
                        CrashAssistantApp.LOGGER.info("Skipping duplicate log as it's already added to the list: " + log.getPath());
                        return;
                    }
                }
                CrashAssistantApp.LOGGER.info("Adding {} from {}", log.getName(), log.getPath().toAbsolutePath().toString());
                logs.add(log);
            }
        }
    }
}
