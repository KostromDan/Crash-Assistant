package dev.kostromdan.mods.crash_assistant.common_config.info;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class CrashAssistantInfo {
    private static final String RESOURCE_PATH = "/crash-assistant.properties";
    private static final Properties INFO = loadInfo();

    private CrashAssistantInfo() {
    }

    public static String getVersion() {
        return get("version");
    }

    public static String get(String key) {
        return INFO.getProperty(key);
    }

    public static String getOrDefault(String key, String defaultValue) {
        return INFO.getProperty(key, defaultValue);
    }

    public static Properties getAll() {
        Properties copy = new Properties();
        copy.putAll(INFO);
        return copy;
    }

    private static Properties loadInfo() {
        Properties properties = new Properties();
        try (InputStream inputStream = CrashAssistantInfo.class.getResourceAsStream(RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Resource not found: " + RESOURCE_PATH);
            }
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + RESOURCE_PATH, e);
        }
        return properties;
    }
}
