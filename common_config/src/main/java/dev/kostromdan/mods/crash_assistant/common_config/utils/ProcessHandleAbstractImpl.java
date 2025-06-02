package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Optional;

/**
 * Abstract implementation of ProcessHelperImpl that provides shared logic
 * for platform-specific implementations (Windows and Unix).
 */
public abstract class ProcessHandleAbstractImpl implements ProcessHelperImpl {

    /**
     * Returns the current JVM process id in a platform-independent manner.
     * On every HotSpot based JDK 8 the {@link RuntimeMXBean#getName()} is
     * of the form <code>&lt;pid&gt;@&lt;hostname&gt;</code>.
     * If the format is unexpected, <code>-1</code> is returned.
     */
    @Override
    public long getCurrentProcessId() {
        RuntimeMXBean mxBean = ManagementFactory.getRuntimeMXBean();
        String jvmName = mxBean.getName();          // e.g. "12345@my-host"
        int atPos = jvmName.indexOf('@');
        if (atPos > 0) {
            try {
                return Long.parseLong(jvmName.substring(0, atPos));
            } catch (NumberFormatException ignore) {
                // fall through
            }
        }
        return -1L;
    }


    /**
     * Gets the command used to start the current process.
     * This is a platform-specific operation that must be implemented by subclasses.
     *
     * @return an Optional containing the command, or empty if not available
     */
    @Override
    public abstract Optional<String> getCurrentProcessCommand();

    /**
     * Gets the start time of the current process in milliseconds since epoch.
     * Default implementation uses getCurrentProcessId() and getProcessStartTime().
     *
     * @return the start time, or -1 if not available
     */
    @Override
    public abstract long getCurrentProcessStartTime();

    /**
     * Gets the start time of a process with the specified ID.
     * This is a platform-specific operation that must be implemented by subclasses.
     *
     * @param pid the process ID
     * @return the start time in milliseconds since epoch, or -1 if not available
     */
    @Override
    public abstract long getProcessStartTime(long pid);

    /**
     * Checks if a process with the specified ID is alive.
     * This is a platform-specific operation that must be implemented by subclasses.
     *
     * @param pid the process ID
     * @return true if the process is alive, false otherwise
     */
    @Override
    public abstract boolean isProcessAlive(long pid);

    /**
     * Gets information about child processes of the current process.
     * This is a platform-specific operation that must be implemented by subclasses.
     *
     * @return a string containing information about child processes
     */
    @Override
    public abstract String getChildProcessesInfo();

    /**
     * Attempts to destroy a process with the specified ID.
     * This is a platform-specific operation that must be implemented by subclasses.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    @Override
    public abstract boolean destroyProcess(long pid);

    /**
     * Attempts to forcibly destroy a process with the specified ID.
     * Default implementation calls destroyProcess() as many platforms don't
     * distinguish between normal and forcible termination.
     *
     * @param pid the process ID
     * @return true if the process was successfully destroyed, false otherwise
     */
    @Override
    public abstract boolean destroyProcessForcibly(long pid);
}