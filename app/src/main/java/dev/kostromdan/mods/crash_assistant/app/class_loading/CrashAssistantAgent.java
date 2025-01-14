package dev.kostromdan.mods.crash_assistant.app.class_loading;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;
import java.util.jar.JarFile;

public class CrashAssistantAgent {
    public static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation instrumentation) throws IOException {
        CrashAssistantAgent.instrumentation = instrumentation;
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        CrashAssistantAgent.instrumentation = instrumentation;
    }

    public static void appendJarFile(String file) throws IOException {
        if (instrumentation != null) {
            instrumentation.appendToSystemClassLoaderSearch(new JarFile(Paths.get(file).toFile()));
        }
    }
}
