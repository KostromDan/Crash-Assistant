package dev.kostromdan.mods.crash_assistant.common_config.utils;

/**
 * Unix/Linux‑specific implementation of {@link ProcessHandleAbstractImpl}
 */
public abstract class ProcessHandleUnixAbstractImpl extends ProcessHandleAbstractImpl {
    @Override
    public boolean isProcessAlive(long pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ps", "-p", String.valueOf(pid));
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public String getChildProcessesInfo() {
        return ""; // Not implemented
    }

    /**
     * Attempts to terminate the given process gracefully (SIGTERM).
     * Returns true if the kill command returned exit code 0.
     */
    @Override
    public boolean destroyProcess(long pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("kill", String.valueOf(pid));
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Attempts to forcefully kill the given process (SIGKILL).
     * Returns true if the kill -9 command returned exit code 0.
     */
    @Override
    public boolean destroyProcessForcibly(long pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("kill", "-9", String.valueOf(pid));
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
