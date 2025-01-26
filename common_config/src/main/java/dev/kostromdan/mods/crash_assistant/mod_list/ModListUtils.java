package dev.kostromdan.mods.crash_assistant.mod_list;

import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;

public class ModListUtils {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Path USERNAME_FILE = Paths.get("local", "crash_assistant", "username.info");
    private static final Path MODS_FOLDER = Paths.get("mods");
    private static final Path RESOURCEPACKS_FOLDER = Paths.get("resourcepacks");
    private static final Path JSON_FILE = Paths.get("config", "crash_assistant", "modlist.json");
    public static String currentUsername = "";


    public static LinkedHashSet<Mod> getCurrentModList() {
        try {
            LinkedHashSet<Mod> filenames = new LinkedHashSet<>();
            if (Files.exists(MODS_FOLDER)) {
                Files.list(MODS_FOLDER).sorted(new PathComparator()).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (Files.isRegularFile(path) && filename.endsWith(".jar")) {
                        filenames.add(ModDataParser.parseModData(path));
                    }
                });
            }
            if (Files.exists(RESOURCEPACKS_FOLDER) && CrashAssistantConfig.getBoolean("modpack_modlist.add_resourcepacks")) {
                Files.list(RESOURCEPACKS_FOLDER).sorted(new PathComparator()).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (Files.isDirectory(path) || filename.endsWith(".zip")) {
                        filenames.add(new Mod(filename + " (resourcepack)", null, null));
                    }
                });
            }
            return filenames;
        } catch (Exception e) {
            LOGGER.error("Error while getting current mod list: ", e);
        }
        return new LinkedHashSet<>();
    }

    public static LinkedHashSet<Mod> getSavedModList() {
        try {
            if (Files.exists(JSON_FILE)) {
                String json = new String(Files.readAllBytes(JSON_FILE));
                return Mod.GSON.fromJson(json, Mod.TYPE);

            }
        } catch (Exception e) {
            LOGGER.error("Error while getting Modlist", e);

        }
        return new LinkedHashSet<>();
    }

    public static void saveCurrentModList() {
        try {
            try (FileWriter writer = new FileWriter(JSON_FILE.toFile())) {
                String jsonOutput = Mod.GSON.toJson(getCurrentModList(), Mod.TYPE);
                writer.write(jsonOutput);
            }

            LOGGER.info("Modlist saved to " + JSON_FILE);
        } catch (Exception e) {
            LOGGER.error("Error while saving Modlist", e);
        }
    }

    public static String getCurrentUsername() {
        if (currentUsername.isEmpty() && Files.exists(ModListUtils.USERNAME_FILE)) {
            try {
                currentUsername = new String(Files.readAllBytes(ModListUtils.USERNAME_FILE));
            } catch (Exception ignored) {
            }
        }
        return currentUsername;
    }
}
