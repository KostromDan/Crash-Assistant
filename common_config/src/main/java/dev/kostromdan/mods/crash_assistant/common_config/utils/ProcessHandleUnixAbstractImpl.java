package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Unix/Linux‑specific implementation of {@link ProcessHandleAbstractImpl}
 */
public abstract class ProcessHandleUnixAbstractImpl extends ProcessHandleAbstractImpl {
    @Override
    public boolean isProcessAlive(long pid) {
        try {
            String stat = execAndReadFirst("ps", "-p", String.valueOf(pid), "-o", "stat=");
            if (stat == null) return false;
            stat = stat.trim();
            if (stat.isEmpty()) return false;
            char c = stat.charAt(0);
            return c != 'Z' && c != 'X';
        } catch (Throwable ignored) {
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

    /**
     * Executes <code>ps …</code> and returns the very first line of output, or {@code null}.
     */
    static String execAndReadFirst(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            return br.readLine();
        } finally {
            p.destroy();
        }
    }
}
