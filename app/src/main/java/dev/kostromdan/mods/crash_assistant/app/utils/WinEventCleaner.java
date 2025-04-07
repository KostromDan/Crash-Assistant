package dev.kostromdan.mods.crash_assistant.app.utils;

import java.io.File;
import java.util.Arrays;

public class WinEventCleaner {

    /**
     * Scans the root directory for files named "win_event<digits>.txt"
     * and deletes all but the five files with the highest numeric values.
     */
    public static void cleanOldWinEventFiles() {
        File dir = new File(".");
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalStateException("Root directory is not accessible.");
        }

        // Filter files matching the pattern "win_event<digits>.txt"
        File[] eventFiles = dir.listFiles((d, name) -> name.matches("win_event\\d+\\.txt"));
        if (eventFiles == null || eventFiles.length <= 5) {
            // Nothing to delete if there are 5 or fewer files
            return;
        }

        // Sort files by the numeric value in their names (ascending order)
        Arrays.sort(eventFiles, (f1, f2) -> {
            long num1 = extractNumber(f1.getName());
            long num2 = extractNumber(f2.getName());
            return Long.compare(num1, num2);
        });

        // Delete all but the 5 newest files (i.e., the files with the smallest numbers)
        int filesToDelete = eventFiles.length - 5;
        for (int i = 0; i < filesToDelete; i++) {
            if (!eventFiles[i].delete()) {
                System.err.println("Failed to delete file: " + eventFiles[i].getAbsolutePath());
            }
        }
    }

    /**
     * Extracts the numeric portion from a filename formatted as "win_event<digits>.txt".
     *
     * @param fileName the name of the file
     * @return the numeric value extracted from the file name
     */
    private static long extractNumber(String fileName) {
        String numericPart = fileName.replace("win_event", "").replace(".txt", "");
        return Long.parseLong(numericPart);
    }
}

