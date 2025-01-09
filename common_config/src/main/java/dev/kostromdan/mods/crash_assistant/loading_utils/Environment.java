package dev.kostromdan.mods.crash_assistant.loading_utils;

public enum Environment {
    SERVER,
    CLIENT;

    private static Environment currentEnvironment = SERVER;

    public static void setEnvironment(Environment environment) {
        currentEnvironment = environment;
    }

    public static Environment getCurrentEnvironment() {
        return currentEnvironment;
    }
}
