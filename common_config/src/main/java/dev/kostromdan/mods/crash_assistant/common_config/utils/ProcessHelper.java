package dev.kostromdan.mods.crash_assistant.common_config.utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.ComparableVersion;
import oshi.SystemInfo;

import java.lang.ProcessHandle;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProcessHelper {
    public static long getCurrentProcessId() {
        return ProcessHandle.current().pid();
    }

    public static Optional<String> getCurrentProcessCommand() {
        return ProcessHandle.current().info().command();
    }

    public static long getCurrentProcessStartTime() {
        return ProcessHandle.current().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    public static long getProcessStartTime(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return -1;
        return processHandle.get().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    public static boolean isProcessAlive(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        return processHandle.isPresent() && processHandle.get().isAlive();
    }

    public static String getChildProcessesInfo() {
        return String.join("\n", ProcessHandle.current().children()
                .map(child -> child.pid() + ": " + child.info().startInstant().get().toEpochMilli())
                .collect(Collectors.toList()));
    }

    public static boolean destroyProcess(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return false;
        return processHandle.get().destroy();
    }

    public static boolean destroyProcessForcibly(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return false;
        return processHandle.get().destroyForcibly();
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

    public static String getJavaVersion() {
        return Runtime.version().toString();
    }

    public static List<Class<?>> getNeededForAppClasses() {
        List<Class<?>> classes = new java.util.ArrayList<>();
        classes.add(org.apache.logging.log4j.LogManager.class);
        classes.add(org.apache.logging.log4j.core.Core.class);
        classes.add(com.google.gson.Gson.class);
        classes.add(org.apache.commons.io.input.ReversedLinesFileReader.class);
        classes.add(com.sun.jna.Memory.class);
        classes.add(com.sun.jna.platform.win32.Tlhelp32.class);
        return classes;
    }

    public static String getProcessorName() {
        try {
            return new SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName();
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
