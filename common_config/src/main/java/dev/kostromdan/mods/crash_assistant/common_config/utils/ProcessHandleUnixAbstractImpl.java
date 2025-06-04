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

    @Override
    public boolean destroyProcess(long pid) {
        return false; // Not implemented
    }

    @Override
    public boolean destroyProcessForcibly(long pid) {
        return false; // Not implemented
    }
}
