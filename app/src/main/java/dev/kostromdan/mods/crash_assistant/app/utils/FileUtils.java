package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Map;

public interface FileUtils {
    static void addIfExists(Map<String, Path> map, Path path) {
        addIfExistsAndModified(map, path.getFileName().toString(), path, false);
    }

    static void addIfExists(Map<String, Path> map, String fileName, Path path) {
        addIfExistsAndModified(map, fileName, path, false);
    }

    static void addIfExistsAndModified(Map<String, Path> map, Path path) {
        addIfExistsAndModified(map, path.getFileName().toString(), path, true);
    }

    static void addIfExistsAndModified(Map<String, Path> map, String fileName, Path path) {
        addIfExistsAndModified(map, fileName, path, true);
    }

    static void addIfExistsAndModified(Map<String, Path> map, String fileName, Path path, boolean checkModified) {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            if (CrashAssistantConfig.getBlacklistedLogs().contains(fileName)) {
                return;
            }
            if (checkModified && path.toFile().lastModified() <= CrashAssistantApp.parentStarted) {
                return;
            }
            try {
                if (Files.size(path) == 0) {
                    CrashAssistantApp.LOGGER.warn("File \"" + path + "\" is empty.");
                    return;
                }
            } catch (IOException e) {
                CrashAssistantApp.LOGGER.error("Error while checking file size \"" + path + "\": ", e);
            }
            map.put(fileName, path);
        }
    }

    static void removeTmpFiles(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".tmp")) {
                        Files.delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while deleting tmp files: ", e);
        }
    }

    static void removeOldLogsFolder() {
        try {
            org.apache.commons.io.FileUtils.deleteDirectory(Paths.get("local", "crash_assistant", "logs").toFile());
        } catch (Exception ignored) {
        }

    }

    static HashSet<Path> getModifiedFiles(Path dir, String extension) {
        HashSet<Path> filesFound = new HashSet<>();
        if (dir.toFile().exists()) {
            try {
                Files.list(dir).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(extension) && path.toFile().lastModified() >= CrashAssistantApp.parentStarted) {
                        filesFound.add(path);
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return filesFound;
    }

}
