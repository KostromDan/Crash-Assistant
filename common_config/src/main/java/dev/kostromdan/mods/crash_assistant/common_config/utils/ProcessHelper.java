package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.util.Optional;

import com.sun.jna.Library;
import com.sun.jna.Native;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/**
 * Static helpers for working with OS processes.
 * Uses OSHI under the hood for true cross-platform support.
 */
public final class ProcessHelper {
    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final OperatingSystem OS = SYSTEM_INFO.getOperatingSystem();

    private ProcessHelper() { /* no instantiation */ }

    // ==== JNA kill() interface ====
    private interface CLibrary extends Library {
        CLibrary INSTANCE = Native.loadLibrary("c", CLibrary.class);
        int kill(int pid, int sig);
    }

    /**
     * @return this JVM's PID, or -1 on failure
     */
    public static long getCurrentPid() {
        try {
            return OS.getProcessId();
        } catch (UnsatisfiedLinkError | SecurityException e) {
            return -1;
        }
    }

    /**
     * @return this JVM's start time in milliseconds since the epoch,
     *         or -1 if it can't be determined
     */
    public static long getStartTime() {
        return getStartTime(getCurrentPid());
    }

    /**
     * @param pid any process ID
     * @return its start time in milliseconds since the epoch, or -1 if not found
     */
    public static long getStartTime(long pid) {
        OSProcess p = OS.getProcess((int) pid);
        if (p == null) return -1;
        return p.getStartTime() * 1_000L;
    }

    /**
     * @param pid the process ID
     * @return its executable command (full path), wrapped in Optional, or empty if not found
     */
    public static Optional<String> getCommand(long pid) {
        OSProcess p = OS.getProcess((int) pid);
        if (p == null) return Optional.empty();
        String cmd = p.getPath();
        return (cmd == null || cmd.isEmpty())
                ? Optional.empty()
                : Optional.of(cmd);
    }

    /**
     * @return this JVM’s executable command (full path), wrapped in Optional,
     *         or empty if it can’t be determined
     */
    public static Optional<String> getCommand() {
        return getCommand(getCurrentPid());
    }

    /**
     * @param pid the PID to kill
     * @return true if the terminate call was successful
     */
    public static boolean destroyPid(long pid) {
        // SIGTERM == 15
        return CLibrary.INSTANCE.kill((int) pid, 15) == 0;
    }

    /**
     * @param pid any process ID
     * @return true if a process with that PID exists right now
     */
    public static boolean isPresent(long pid) {
        return OS.getProcess((int) pid) != null;
    }

    /**
     * Checks that the PID is still running *and* has not been recycled since
     * the given startTime.
     *
     * @param pid           the process ID to check
     * @param originalStart the start time you previously fetched (ms since epoch)
     * @return true if still present and its startTime ≥ originalStart
     */
    public static boolean isPresentAndNotReused(long pid, long originalStart) {
        OSProcess p = OS.getProcess((int) pid);
        if (p == null) return false;
        long nowStart = p.getStartTime() * 1_000L;
        return nowStart == originalStart;
    }

    public static String getProcessorName(){
        return new SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName();
    }
}
