package dev.kostromdan.mods.crash_assistant.common_config.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CrashAssistantLocalConfig {
    private static final FileConfig config;
    private static final Path CONFIG_PATH = Paths.get("local", "crash_assistant", "local_config.json");

    static {
        try {
            java.nio.file.Files.createDirectories(CONFIG_PATH.getParent());
        } catch (Exception ignored) {}
        config = FileConfig.builder(CONFIG_PATH, JsonFormat.fancyInstance()).build();
        load();
    }

    public static void load() {
        try{
            config.load();
        }catch(Exception e){
            JarInJarHelper.LOGGER.error("Failed to load local_config.json... Resetting.", e);
            config.clear();
        }
    }

    public static void save() {
        config.save();
    }

    public static boolean getBoolean(String key) {
        return config.get(key);
    }

    public static Object get(String key) {
        return config.get(key);
    }

    public static void set(String key, Object value) {
        config.set(key, value);
        save();
    }
}
