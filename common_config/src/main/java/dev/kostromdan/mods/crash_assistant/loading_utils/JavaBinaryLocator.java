package dev.kostromdan.mods.crash_assistant.loading_utils;

import java.util.Optional;

public interface JavaBinaryLocator {
    static String getJavaBinary(ProcessHandle currentProcess) {
        Optional<String> javaBinary = currentProcess.info().command();
        if (javaBinary.isEmpty()) {
            throw new IllegalStateException("Unable to determine the java binary path of current JVM. Crash Assistant won't work.");
        }
        return javaBinary.get();
    }
}
