package dev.kostromdan.mods.crash_assistant.common_config.utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraftforge.fml.crash_assistant.ExitVMBypass;

import java.lang.reflect.Method;
import java.util.List;
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
        ExitVMBypass.exit(status);
    }

    public static String getJavaVersion() {
        return System.getProperty("java.runtime.version", "UNDEFINED");
    }

    public static List<Class<?>> getNeededForAppClasses() {
        List<Class<?>> classes = new java.util.ArrayList<>();
        classes.add(org.apache.logging.log4j.LogManager.class);
        classes.add(org.apache.logging.log4j.core.Core.class);
        classes.add(org.apache.commons.io.input.ReversedLinesFileReader.class);
        classes.add(com.sun.jna.Memory.class);
        classes.add(com.sun.jna.platform.win32.Tlhelp32.class);
        return classes;
    }

    public static String getProcessorName() {
        try {
            try {
                Class<?> sysInfoCls = Class.forName("oshi.SystemInfo");
                Object sysInfo = sysInfoCls.getDeclaredConstructor().newInstance();

                Object hardware = sysInfoCls.getMethod("getHardware").invoke(sysInfo);

                Object[] processors = (Object[]) hardware.getClass()
                        .getMethod("getProcessors")
                        .invoke(hardware);
                return String.format("%s", processors[0]).replaceAll("\\s+", " ");
            } catch (NoSuchMethodError | NoSuchMethodException ex) {
                // new SystemInfo()
                Class<?> systemInfoCls = Class.forName("oshi.SystemInfo");
                Object systemInfo = systemInfoCls.getDeclaredConstructor().newInstance();

                // getHardware()
                Method mGetHardware = systemInfoCls.getMethod("getHardware");
                Object hardware = mGetHardware.invoke(systemInfo);

                // getProcessor()
                Method mGetProcessor = hardware.getClass().getMethod("getProcessor");
                Object processor = mGetProcessor.invoke(hardware);

                // getProcessorIdentifier()
                Method mGetIdentifier = processor.getClass().getMethod("getProcessorIdentifier");
                Object identifier = mGetIdentifier.invoke(processor);

                // getName()
                Method mGetName = identifier.getClass().getMethod("getName");
                return (String) mGetName.invoke(identifier);
            }
        } catch (Throwable e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.matches(".*Failed to create temporary file for .* library: JNA temporary directory .* does not exist.*")) {
                JarInJarHelper.LOGGER.error(errorMessage + "\n   \n" +
                        "   Most likely you have permission issues in your file system.\n" +
                        "   OSHI failed init because it failed to create its tmp files for natives.\n" +
                        "   This won't crash Vanilla, but can crash many other mods using OSHI, like Embeddium.\n" +
                        "   Try reinstalling your launcher / trying another launcher, make sure to NOT activate admin rights on install,\n" +
                        "   as this is most likely the cause of this permission issue.\n    \n" +
                        "   If you seeing Crash Assistant in the stacktrace somewhere upper, it's not the cause of the crash!\n" +
                        "   It's just the first thing tried to use OSHI, which failed to init.\n   ");
            } else {
                JarInJarHelper.LOGGER.error("Error while getting processor name:", e);
            }
            return "UNKNOWN";
        }
    }
}
