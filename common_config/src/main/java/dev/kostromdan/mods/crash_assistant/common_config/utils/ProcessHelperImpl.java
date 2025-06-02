package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.util.Optional;

/**
 * Interface defining process management operations.
 * Implementations provide platform-specific or Java version-specific functionality.
 */
public interface ProcessHelperImpl {
    /**
     * Gets the current process ID.
     *
     * @return the current process ID
     */
    long getCurrentProcessId();

    /**
     * Gets the command used to start the current process.
     *
     * @return an Optional containing the command, or empty if not available
     */
    Optional<String> getCurrentProcessCommand();

    /**
     * Gets the start time of the current process in milliseconds since epoch.
     *
     * @return the start time, or -1 if not available
     */
    long getCurrentProcessStartTime();

    /**
     * Gets the start time of a process with the specified ID.
     *
     * @param pid the process ID
     * @return the start time in milliseconds since epoch, or -1 if not available
     */
    long getProcessStartTime(long pid);

    /**
     * Checks if a process with the specified ID is alive.
     *
     * @param pid the process ID
     * @return true if the process is alive, false otherwise
     */
    boolean isProcessAlive(long pid);

    /**
     * Gets information about child processes of the current process.
     *
     * @return a string containing information about child processes
     */
    String getChildProcessesInfo();

    /**
     * Attempts to destroy a process with the specified ID.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    boolean destroyProcess(long pid);

    /**
     * Attempts to forcibly destroy a process with the specified ID.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    boolean destroyProcessForcibly(long pid);
}