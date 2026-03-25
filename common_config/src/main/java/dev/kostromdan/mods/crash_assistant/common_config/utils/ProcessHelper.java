package dev.kostromdan.mods.crash_assistant.common_config.utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraftforge.fml.crash_assistant.ExitVMBypass;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Utility class for process management operations.
 * This class delegates to an appropriate implementation based on the
 * availability of ProcessHandle and the operating system.
 */
public class ProcessHelper {
    private static final boolean java9orLater = ClassExistenceChecker.classExists("java.lang.ProcessHandle");

    private static class ImplHolder {
        static final ProcessHelperImpl IMPL;

        static {
            if (isJava9orLater()) {
                IMPL = new ProcessHelperProcessHandleImpl();
            } else if (PlatformHelp.isWindows()) {
                IMPL = new ProcessHandleWinImpl();
            } else if (PlatformHelp.isLinux()) {
                IMPL = new ProcessHandleLinuxImpl();
            } else if (PlatformHelp.isMacOS()) {
                IMPL = new ProcessHandleMacOSImpl();
            } else {
                IMPL = null; // Или какой-то DummyImpl
            }
        }
    }

    /**
     * Gets the current process ID.
     *
     * @return the current process ID
     */
    public static long getCurrentProcessId() {
        return ImplHolder.IMPL.getCurrentProcessId();
    }

    /**
     * Gets the command used to start the current process.
     *
     * @return an Optional containing the command, or empty if not available
     */
    public static Optional<String> getCurrentProcessCommand() {
        return ImplHolder.IMPL.getCurrentProcessCommand();
    }

    /**
     * Gets the start time of the current process in milliseconds since epoch.
     *
     * @return the start time, or -1 if not available
     */
    public static long getCurrentProcessStartTime() {
        return ImplHolder.IMPL.getCurrentProcessStartTime();
    }

    /**
     * Gets the start time of a process with the specified ID.
     *
     * @param pid the process ID
     * @return the start time in milliseconds since epoch, or -1 if not available
     */
    public static long getProcessStartTime(long pid) {
        return ImplHolder.IMPL.getProcessStartTime(pid);
    }

    /**
     * Checks if a process with the specified ID is alive.
     *
     * @param pid the process ID
     * @return true if the process is alive, false otherwise
     */
    public static boolean isProcessAlive(long pid) {
        return ImplHolder.IMPL.isProcessAlive(pid);
    }

    /**
     * Gets information about child processes of the current process.
     *
     * @return a string containing information about child processes
     */
    public static String getChildProcessesInfo() {
        return ImplHolder.IMPL.getChildProcessesInfo();
    }

    /**
     * Attempts to destroy a process with the specified ID.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    public static boolean destroyProcess(long pid) {
        return ImplHolder.IMPL.destroyProcess(pid);
    }

    /**
     * Attempts to forcibly destroy a process with the specified ID.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    public static boolean destroyProcessForcibly(long pid) {
        return ImplHolder.IMPL.destroyProcessForcibly(pid);
    }

    /**
     * Exits the current process with the specified status code.
     * Uses System.exit() internally. If needed, uses bypasses to ensure termination,
     * for example in legacy versions.
     *
     * @param status the exit status code to use when terminating the process
     */
    public static void exitProcess(int status) {
        switch (ModVersionsHelper.versionRange) {
            case V_1_7_10:
            case V_1_6_4:
            case V_1_8__1_11_2:
            case V_1_12_2:
                ExitVMBypass.exit(status);
                break;
            default:
                System.exit(status);
                break;
        }
    }

    public static String getJavaVersion() {
        if (isJava9orLater()) {
            try {
                return String.valueOf(Runtime.class.getMethod("version").invoke(null));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to call Runtime.version() on Java 9+", e);
            }
        }
        return System.getProperty("java.runtime.version", "UNDEFINED");
    }

    public static boolean isJava9orLater() {
        return java9orLater;
    }

    public static List<BiPredicate<Path, String>> getAppPredicates() {
        List<BiPredicate<Path, String>> predicates = new ArrayList<>();

        if (ModVersionsHelper.versionRange != ModVersionsHelper.VersionRange.V_1_6_4) {
            predicates.add((path, fileName) -> fileName.startsWith("log4j-api-"));
            predicates.add((path, fileName) -> fileName.startsWith("log4j-core-"));
        }

        predicates.add((path, fileName) -> fileName.startsWith("commons-io-"));

        if (ModVersionsHelper.versionRange == ModVersionsHelper.VersionRange.V_1_18_2__MODERN) {
            predicates.add((path, fileName) -> fileName.startsWith("gson-"));
        }

        if (ModVersionsHelper.versionRange != ModVersionsHelper.VersionRange.V_1_7_10 && ModVersionsHelper.versionRange != ModVersionsHelper.VersionRange.V_1_6_4) {
            predicates.addAll(getJnaPredicates());
        }

        return predicates;
    }

    public static List<BiPredicate<Path, String>> getJnaPredicates() {
        List<BiPredicate<Path, String>> predicates = new ArrayList<>();
        if (ModVersionsHelper.versionRange != ModVersionsHelper.VersionRange.V_1_7_10 && ModVersionsHelper.versionRange != ModVersionsHelper.VersionRange.V_1_6_4) {
            predicates.add((path, fileName) -> fileName.startsWith("jna-") && !fileName.startsWith("jna-platform-"));
            predicates.add((path, fileName) -> fileName.startsWith("jna-platform-"));
            predicates.add((path, fileName) -> fileName.startsWith("platform-"));
            predicates.add((path, fileName) -> fileName.startsWith("oshi-core-"));
        }
        if(ModVersionsHelper.versionRange == ModVersionsHelper.VersionRange.V_1_6_4){
            predicates.add((path, fileName) -> fileName.startsWith("gson-"));
        }
        return predicates;
    }

    public static List<String> getPathsToNeededLibs(List<BiPredicate<Path, String>> predicates) {
        List<String> classPathEntriesForAppProcess = new ArrayList<>();
        for (String pathStr : ArgUtils.getUnsafeClassPathList()) {
            if (!pathStr.endsWith(".jar")) {
                continue;
            }

            Path path = Paths.get(pathStr);
            String fileName = path.getFileName().toString().toLowerCase();

            for (BiPredicate<Path, String> predicate : predicates) {
                if (predicate.test(path, fileName)) {
                    classPathEntriesForAppProcess.add(pathStr);
                    break;
                }
            }
        }
        return classPathEntriesForAppProcess;
    }

    public static String getProcessorName() {
        try {
            try {
                Class<?> systemInfoCls = Class.forName("oshi.SystemInfo");
                Object systemInfo = systemInfoCls.getDeclaredConstructor().newInstance();

                Method mGetHardware = systemInfoCls.getMethod("getHardware");
                Object hardware = mGetHardware.invoke(systemInfo);

                Method mGetProcessor = hardware.getClass().getMethod("getProcessor");
                Object processor = mGetProcessor.invoke(hardware);

                Method mGetIdentifier = processor.getClass().getMethod("getProcessorIdentifier");
                Object identifier = mGetIdentifier.invoke(processor);

                Method mGetName = identifier.getClass().getMethod("getName");
                return (String) mGetName.invoke(identifier);
            } catch (NoSuchMethodError | NoSuchMethodException ex) {
                Class<?> sysInfoCls = Class.forName("oshi.SystemInfo");
                Object sysInfo = sysInfoCls.getDeclaredConstructor().newInstance();

                Object hardware = sysInfoCls.getMethod("getHardware").invoke(sysInfo);

                Object[] processors = (Object[]) hardware.getClass()
                        .getMethod("getProcessors")
                        .invoke(hardware);
                return String.format("%s", processors[0]).replaceAll("\\s+", " ");
            }
        } catch (Throwable e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.matches(".*Failed to create temporary file for /com/sun/jna/.*\\.dll library: .*")) {
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
