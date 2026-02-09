package dev.kostromdan.mods.crash_assistant.app.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UploadedLogsManager {
    private static final Path STORAGE_PATH = Paths.get("local", "crash_assistant", "uploaded_logs.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    public static void saveLog(String name, String url, String deleteToken) {
        lock.writeLock().lock();
        try {
            List<UploadedLog> logs = loadLogsInternal();
            logs.add(new UploadedLog(name, System.currentTimeMillis(), url, deleteToken));
            writeLogs(logs);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to save uploaded log info", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void removeLog(UploadedLog log) {
        lock.writeLock().lock();
        try {
            List<UploadedLog> logs = loadLogsInternal();
            logs.removeIf(l -> l.getUrl().equals(log.getUrl()));
            writeLogs(logs);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to remove uploaded log info", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static List<UploadedLog> getSavedLogs() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(loadLogsInternal());
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to load uploaded logs", e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private static List<UploadedLog> loadLogsInternal() {
        if (!Files.exists(STORAGE_PATH)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(STORAGE_PATH)) {
            List<UploadedLog> logs = GSON.fromJson(reader, new TypeToken<List<UploadedLog>>() {
            }.getType());
            return logs != null ? logs : new ArrayList<>();
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to read uploaded logs file. It might be corrupted.", e);
            backupCorruptedFile();
            return new ArrayList<>();
        }
    }

    private static void backupCorruptedFile() {
        try {
            Path backupPath = getNextBackupPath();
            Files.move(STORAGE_PATH, backupPath);
            CrashAssistantApp.LOGGER.warn("Corrupted uploaded logs file moved to: {}", backupPath.toAbsolutePath());
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to backup corrupted uploaded logs file", e);
        }
    }

    private static Path getNextBackupPath() {
        Path baseBackup = Paths.get(STORAGE_PATH.toString() + ".bak");
        if (!Files.exists(baseBackup)) {
            return baseBackup;
        }
        int i = 1;
        while (true) {
            Path nextBackup = Paths.get(STORAGE_PATH.toString() + ".bak." + i);
            if (!Files.exists(nextBackup)) {
                return nextBackup;
            }
            i++;
        }
    }

    private static void writeLogs(List<UploadedLog> logs) throws IOException {
        Path parent = STORAGE_PATH.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(STORAGE_PATH)) {
            GSON.toJson(logs, writer);
        }
    }
}