package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public interface FileUtils {
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
        try {
            Files.delete(Paths.get("logs", "crash_assistant", "latest.log")); // renamed to crash_assistant_app.log
        } catch (Exception ignored) {
        }
    }

    static HashSet<Path> getModifiedFiles(Path dir, String extension) {
        HashSet<Path> filesFound = new HashSet<>();
        if (dir.toFile().exists()) {
            try {
                Files.list(dir).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(extension) && path.toFile().lastModified() >= Boot.parentStarted) {
                        filesFound.add(path);
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return filesFound;
    }

    static boolean isCurseForgeEnv(){
        try {
            Path curseForgeDir = Paths.get("").toAbsolutePath().getParent().getParent();
            List<String> curseForgeDirContents = Files.list(curseForgeDir).map(dirPath -> dirPath.getFileName().toString().toLowerCase()).collect(Collectors.toList());
            if (curseForgeDirContents.contains("instances") && curseForgeDirContents.contains("install")) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static boolean folderNLevelsUpperNameContains(int levels, String sToCheck){
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            path.append("../");
        }
        return Paths.get(path.toString()).toAbsolutePath().normalize().getFileName().toString().contains(sToCheck);
    }


}
