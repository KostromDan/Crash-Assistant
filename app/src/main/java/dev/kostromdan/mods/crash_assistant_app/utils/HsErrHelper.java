package dev.kostromdan.mods.crash_assistant_app.utils;

import dev.kostromdan.mods.crash_assistant_app.CrashAssistantApp;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class HsErrHelper {
    /**
     * Current Minecraft can have same pid as some old Minecraft process, which crashed with hs_err.
     * To not confuse user with incorrect old hs_err log, we remove it before start.
     */
    public static void removeHsErrLog(long pid) {
        locateHsErrLog(pid).ifPresent(logPath -> {
            try {
                Files.deleteIfExists(logPath);
            } catch (IOException e) {
                CrashAssistantApp.LOGGER.error("Error while deleting hs_err log file", e);
            }
        });
    }

    /**
     * Locates the hs_err_pid<pid>.log file in the root folder.
     * @param pid Process ID to search for.
     * @return An Optional containing the Path of the log file if it exists, otherwise an empty Optional.
     */
    public static Optional<Path> locateHsErrLog(long pid) {
        Path rootFolder = Paths.get(".");
        String fileName = "hs_err_pid" + pid + ".log";
        Path logPath = rootFolder.resolve(fileName);

        if (Files.exists(logPath)) {
            return Optional.of(logPath);
        } else {
            return Optional.empty();
        }
    }
}
