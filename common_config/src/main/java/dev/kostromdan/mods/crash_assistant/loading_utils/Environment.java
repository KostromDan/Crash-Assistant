package dev.kostromdan.mods.crash_assistant.loading_utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public enum Environment {
    SERVER,
    CLIENT;

    private static final Path currentEnvironmentPath = Paths.get("local", "crash_assistant", "environment.info");
    private static Environment currentEnvironment = null;

    public static void setEnvironment(Environment environment) {
        currentEnvironment = environment;
        try {
            Files.createDirectories(currentEnvironmentPath.getParent());
            Files.write(currentEnvironmentPath, environment.toString().getBytes());
        } catch (IOException e) {
            JarInJarHelper.LOGGER.error(e);
        }
    }

    public static Environment getCurrentEnvironment() {
        if (currentEnvironment == null) {
            try {
                currentEnvironment = Environment.valueOf(new String(Files.readAllBytes(currentEnvironmentPath)));
            } catch (IOException e) {
                currentEnvironment = Environment.SERVER;
                JarInJarHelper.LOGGER.error(e);
            }
        }
        return currentEnvironment;
    }
}
