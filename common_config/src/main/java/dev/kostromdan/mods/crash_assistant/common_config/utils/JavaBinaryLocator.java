package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.util.Optional;

public interface JavaBinaryLocator {
    static String getJavaBinary() {
        Optional<String> javaBinary = ProcessHandle.current().info().command();
        if (javaBinary.isEmpty()) {
            throw new IllegalStateException("Unable to determine the java binary path of current JVM. Crash Assistant won't work.");
        }
        return javaBinary.get();
    }
}
