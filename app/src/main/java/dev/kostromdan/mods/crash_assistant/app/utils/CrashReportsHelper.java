package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.function.Predicate;

public class CrashReportsHelper {

    /**
     * Retrieves all relevant files from the specified directory that were modified
     * after the parent process started and match the provided filename filter.
     *
     * @param dir The directory to scan for files
     * @param filenameFilter A predicate that tests if a filename should be included
     * @return A set of paths representing all relevant files in the directory.
     * If the directory does not exist or an error occurs, an empty set is returned.
     */
    public static HashSet<Path> getRelevantFiles(Path dir, Predicate<String> filenameFilter) {
        HashSet<Path> relevantFiles = new HashSet<>();

        if (!Files.exists(dir)) {
            return relevantFiles;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) && 
                    entry.getFileName() != null && 
                    filenameFilter.test(entry.getFileName().toString()) &&
                    entry.toFile().lastModified() >= Boot.parentStarted) {
                    relevantFiles.add(entry);
                }
            }
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Error while scanning files in " + dir + ": ", e);
            return new HashSet<>();
        }
        return relevantFiles;
    }
}
