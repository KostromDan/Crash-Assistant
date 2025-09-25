package dev.kostromdan.mods.crash_assistant.common_config.utils;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraftforge.fml.crash_assistant.ExitVMBypass;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        ExitVMBypass.exit(status);
    }

    public static String getJavaVersion() {
        return System.getProperty("java.runtime.version", "UNDEFINED");
    }

    public static List<Class<?>> getNeededForAppClasses() {
        List<Class<?>> classes = new java.util.ArrayList<>();
        classes.add(org.apache.logging.log4j.LogManager.class);
        classes.add(org.apache.logging.log4j.core.LoggerContext.class);
        classes.add(org.apache.commons.io.input.ReversedLinesFileReader.class);
        classes.add(com.sun.jna.Memory.class);
        classes.add(com.sun.jna.platform.win32.Tlhelp32.class);
        return classes;
    }

    public static String getProcessorName() {
        try {
            List<String> cmd;
            if (PlatformHelp.isWindows()) {
                String name = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "ProcessorNameString");
                if (name != null) {
                    name = name.trim();
                    if (!name.isEmpty()) return name;
                }
                return "UNKNOWN";
            } else if (PlatformHelp.isLinux()) {
                cmd = Arrays.asList("bash", "-c",
                    "grep -m1 \"model name\" /proc/cpuinfo | cut -d ':' -f2");
            } else if (PlatformHelp.isMacOS()) {
                cmd = Arrays.asList("sysctl", "-n", "machdep.cpu.brand_string");
            } else {
                return "UNKNOWN";
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                return "UNKNOWN";
            }

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || (PlatformHelp.isWindows() && line.equalsIgnoreCase("Name"))) {
                        continue;
                    }
                    return line;
                }
            }
        } catch (Throwable e) {
            JarInJarHelper.LOGGER.error("Error while getting processor name:", e);
            return "UNKNOWN";
        }

        return "UNKNOWN";
    }
}
