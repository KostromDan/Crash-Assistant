package dev.kostromdan.mods.crash_assistant.app.class_loading;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Boot {
    public static String log4jApi = null;
    public static String log4jCore = null;
    public static String googleGson = null;
    public static String commonIo = null;

    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        for (int i = 0; i < args.length; i++) {
            if ("-log4jApi".equals(args[i]) && i + 1 < args.length) {
                log4jApi = args[i + 1];
            } else if ("-log4jCore".equals(args[i]) && i + 1 < args.length) {
                log4jCore = args[i + 1];
            } else if ("-googleGson".equals(args[i]) && i + 1 < args.length) {
                googleGson = args[i + 1];
            } else if ("-commonIo".equals(args[i]) && i + 1 < args.length) {
                commonIo = args[i + 1];
            }
        }

        List<String> missingParameters = getMissingParameters();
        if (!missingParameters.isEmpty()) {
            System.err.println("Missing required parameters: " + String.join(", ", missingParameters) +
                    "\nIf you trying to run app from dev env, run CrashAssistantApp.");
            System.exit(-1);
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
        return missingParameters;
    }
}
