package dev.kostromdan.mods.crash_assistant.common_config.utils;

import oshi.SystemInfo;

import java.time.Instant;
import java.util.Optional;
import java.lang.ProcessHandle;

/**
 * Static helpers for working with OS processes.
 * Uses Java's ProcessHandle API for cross-platform support.
 */
public final class ProcessHelper {

    private ProcessHelper() { /* no instantiation */ }


    /**
     * @return this JVM's PID, or -1 on failure
     */
    public static long getCurrentPid() {
        try {
            return ProcessHandle.current().pid();
        } catch (SecurityException e) {
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
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return -1;
        return processHandle.get().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    /**
     * @param pid the process ID
     * @return its executable command (full path), wrapped in Optional, or empty if not found
     */
    public static Optional<String> getCommand(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return Optional.empty();
        return processHandle.get().info().command();
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
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        return processHandle.map(handle -> handle.destroy()).orElse(false);
    }

    /**
     * @param pid any process ID
     * @return true if a process with that PID exists right now
     */
    public static boolean isPresent(long pid) {
        return ProcessHandle.of(pid).isPresent();
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
        if (!isPresent(pid)) return false;
        long nowStart = getStartTime(pid);
        return nowStart == originalStart;
    }

    public static String getProcessorName(){
        return new SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName();
    }
}
