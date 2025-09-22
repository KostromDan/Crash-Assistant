package dev.kostromdan.mods.crash_assistant.common_config.utils;

import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.Optional;

/**
 * Utility class for process management operations.
 * This class delegates to an appropriate implementation based on the
 * availability of ProcessHandle and the operating system.
 */
public class ProcessHelper {
    private static final ProcessHelperImpl impl;

    static {
        ProcessHelperImpl tempImpl = null;

        // Try to use ProcessHandle via reflection first
        try {
            Class.forName("java.lang.ProcessHandle");
            tempImpl = new ProcessHelperProcessHandleImpl();
        } catch (Exception e) {
            // ProcessHandle not available, use OS-specific implementation
            String osName = System.getProperty("os.name").toLowerCase();
            if (PlatformHelp.isWindows()) {
                tempImpl = new ProcessHandleWinImpl();
            } else if (PlatformHelp.isLinux()) {
                // Assume Unix/macOS/Linux for all other OS
                tempImpl = new ProcessHandleLinuxImpl();
            } else if (PlatformHelp.isMacOS()) {
                tempImpl = new ProcessHandleMacOSImpl();
            }
        }

        impl = tempImpl;
    }

    /**
     * Gets the current process ID.
     *
     * @return the current process ID
     */
    public static long getCurrentProcessId() {
        return impl.getCurrentProcessId();
    }

    /**
     * Gets the command used to start the current process.
     *
     * @return an Optional containing the command, or empty if not available
     */
    public static Optional<String> getCurrentProcessCommand() {
        return impl.getCurrentProcessCommand();
    }

    /**
     * Gets the start time of the current process in milliseconds since epoch.
     *
     * @return the start time, or -1 if not available
     */
    public static long getCurrentProcessStartTime() {
        return impl.getCurrentProcessStartTime();
    }

    /**
     * Gets the start time of a process with the specified ID.
     *
     * @param pid the process ID
     * @return the start time in milliseconds since epoch, or -1 if not available
     */
    public static long getProcessStartTime(long pid) {
        return impl.getProcessStartTime(pid);
    }

    /**
     * Checks if a process with the specified ID is alive.
     *
     * @param pid the process ID
     * @return true if the process is alive, false otherwise
     */
    public static boolean isProcessAlive(long pid) {
        return impl.isProcessAlive(pid);
    }

    /**
     * Gets information about child processes of the current process.
     *
     * @return a string containing information about child processes
     */
    public static String getChildProcessesInfo() {
        return impl.getChildProcessesInfo();
    }

    /**
     * Attempts to destroy a process with the specified ID.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    public static boolean destroyProcess(long pid) {
        return impl.destroyProcess(pid);
    }

    /**
     * Attempts to forcibly destroy a process with the specified ID.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    public static boolean destroyProcessForcibly(long pid) {
        return impl.destroyProcessForcibly(pid);
    }

    /**
     * Exits the current process with the specified status code.
     * Uses System.exit() internally. If needed, uses bypasses to ensure termination,
     * for example in legacy versions.
     *
     * @param status the exit status code to use when terminating the process
     */
    public static void exitProcess(int status) {
        System.exit(status);
    }
}
