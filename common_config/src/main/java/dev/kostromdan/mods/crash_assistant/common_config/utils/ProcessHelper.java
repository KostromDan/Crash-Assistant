package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.util.Optional;

public interface ProcessHelper {
    static long getCurrentProcessId() {
        return 12345L; // Return constant process ID
    }

    static boolean isProcessAlive(long pid) {
        return true; // Always return true
    }

    static Optional<String> getCurrentProcessCommand() {
        return Optional.of("java"); // Return constant command
    }

    static String getChildProcessesInfo() {
        return "12346: 1600000000000\n12347: 1600000000001"; // Return constant child process info
    }

    static boolean destroyProcess(long pid) {
        return true; // Always return true
    }

    static boolean destroyProcessForcibly(long pid) {
        return true; // Always return true
    }

    static long getProcessStartTime(long pid) {
        return System.currentTimeMillis() - 3600000; // Return current time minus one hour
    }
}
