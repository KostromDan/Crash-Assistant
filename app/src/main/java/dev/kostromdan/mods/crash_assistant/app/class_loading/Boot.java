package dev.kostromdan.mods.crash_assistant.app.class_loading;

import dev.kostromdan.mods.crash_assistant.loading_utils.JavaBinaryLocator;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Boot {
    public static String log4jApi = null;
    public static String log4jCore = null;
    public static String googleGson = null;
    public static String commonIo = null;
    public static String jarPath = null;
    public static boolean recursiveStart = false;
    public static List<String> JVM_ARGS = ManagementFactory.getRuntimeMXBean().getInputArguments();
    public static List<String> APP_ARGS;


    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        APP_ARGS = List.of(args);
        for (int i = 0; i < args.length; i++) {
            if ("-log4jApi".equals(args[i]) && i + 1 < args.length) {
                log4jApi = args[i + 1];
            } else if ("-log4jCore".equals(args[i]) && i + 1 < args.length) {
                log4jCore = args[i + 1];
            } else if ("-googleGson".equals(args[i]) && i + 1 < args.length) {
                googleGson = args[i + 1];
            } else if ("-commonIo".equals(args[i]) && i + 1 < args.length) {
                commonIo = args[i + 1];
            } else if ("-jarPath".equals(args[i]) && i + 1 < args.length) {
                jarPath = args[i + 1];
            } else if ("-recursiveStart".equals(args[i])) {
                recursiveStart = true;
            }
        }

        List<String> missingParameters = getMissingParameters();
        if (!missingParameters.isEmpty()) {
            System.err.println("Missing required parameters: " + String.join(", ", missingParameters) +
                    "\nIf you trying to run app from dev env, run CrashAssistantApp.");
            System.exit(-1);
        }

        /**
         * If Minecraft JVM terminated by windows itself, all child processes will be also terminated.
         * So Crash Assistant can't be child process. This way we make Crash Assistant completely independent process.
         */
        if (!recursiveStart) {
            List<String> argsList = new ArrayList<>();
            argsList.add(JavaBinaryLocator.getJavaBinary(ProcessHandle.current()));
            argsList.addAll(JVM_ARGS);
            argsList.add("-jar");
            argsList.add(jarPath);
            argsList.addAll(APP_ARGS);
            argsList.add("-recursiveStart");
            ProcessBuilder pb = new ProcessBuilder(argsList);
            pb.start();
            System.exit(0);
        }

        CrashAssistantAgent.appendJarFile(log4jApi);
        CrashAssistantAgent.appendJarFile(log4jCore);
        CrashAssistantAgent.appendJarFile(googleGson);
        CrashAssistantAgent.appendJarFile(commonIo);

        Class<?> crashAssistantAppClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp");
        Method mainMethod = crashAssistantAppClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }

    private static List<String> getMissingParameters() {
        List<String> missingParameters = new ArrayList<>();
        if (log4jApi == null) missingParameters.add("-log4jApi");
        if (log4jCore == null) missingParameters.add("-log4jCore");
        if (googleGson == null) missingParameters.add("-googleGson");
        if (commonIo == null) missingParameters.add("-commonIo");
        if (jarPath == null) missingParameters.add("-jarPath");
        return missingParameters;
    }
}
