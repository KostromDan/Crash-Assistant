package dev.kostromdan.mods.crash_assistant.app.class_loading;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Boot {
    public static String log4jApi = null;
    public static String log4jCore = null;
    public static String googleGson = null;
    public static String nightConfigCore = null;
    public static String nightConfigToml = null;

    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        for (int i = 0; i < args.length; i++) {
            if ("-log4jApi".equals(args[i]) && i + 1 < args.length) {
                log4jApi = args[i + 1];
            } else if ("-log4jCore".equals(args[i]) && i + 1 < args.length) {
                log4jCore = args[i + 1];
            } else if ("-googleGson".equals(args[i]) && i + 1 < args.length) {
                googleGson = args[i + 1];
            } else if ("-nightConfigCore".equals(args[i]) && i + 1 < args.length) {
                nightConfigCore = args[i + 1];
            } else if ("-nightConfigToml".equals(args[i]) && i + 1 < args.length) {
                nightConfigToml = args[i + 1];
            }
        }

        CrashAssistantAgent.appendJarFile(log4jApi);
        CrashAssistantAgent.appendJarFile(log4jCore);
        CrashAssistantAgent.appendJarFile(googleGson);
        CrashAssistantAgent.appendJarFile(nightConfigCore);
        CrashAssistantAgent.appendJarFile(nightConfigToml);

        Class<?> crashAssistantAppClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp");
        Method mainMethod = crashAssistantAppClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }
}