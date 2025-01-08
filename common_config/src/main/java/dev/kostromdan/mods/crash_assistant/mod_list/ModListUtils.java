package dev.kostromdan.mods.crash_assistant.mod_list;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ModListUtils {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Path USERNAME_FILE = Paths.get("local", "crash_assistant", "username.info");
    private static final Path MODS_FOLDER = Paths.get("mods");
    private static final Path JSON_FILE = Paths.get("config", "crash_assistant", "modlist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static HashSet<String> getCurrentModList() {
        try {
            HashSet<String> filenames = new HashSet<>();
            if (!Files.exists(MODS_FOLDER)) {
                return filenames;
            }
            Files.list(MODS_FOLDER).forEach(path -> {
                if (Files.isRegularFile(path)) {
                    filenames.add(path.getFileName().toString());
                }
            });
            return filenames;
        } catch (Exception e) {
            LOGGER.error("Error while getting current mod list: ", e);
        }
        return new HashSet<>();
    }

    public static HashSet<String> getSavedModList() {
        try {
            if (Files.exists(JSON_FILE)) {
                String json = new String(Files.readAllBytes(JSON_FILE));
                Type setType = new TypeToken<HashSet<String>>() {
                }.getType();
                return GSON.fromJson(json, setType);
            }
        } catch (Exception e) {
            LOGGER.error("Error while getting Modlist", e);
        }
        return new HashSet<>();
    }

    public static void saveCurrentModList() {
        try {
            try (FileWriter writer = new FileWriter(JSON_FILE.toFile())) {
                GSON.toJson(getCurrentModList(), writer);
            }

            LOGGER.info("Modlist saved to " + JSON_FILE);
        } catch (Exception e) {
            LOGGER.error("Error while saving Modlist", e);
        }
    }

    public static ModListDiff getDiff() {
        HashSet<String> currentModList = getCurrentModList();
        HashSet<String> savedModList = getSavedModList();

        return new ModListDiff(
                // Added mods: present in current but not in saved
                currentModList.stream().filter(mod -> !savedModList.contains(mod)).collect(Collectors.toCollection(TreeSet::new)),

                // Removed mods: present in saved but not in current
                savedModList.stream().filter(mod -> !currentModList.contains(mod)).collect(Collectors.toCollection(TreeSet::new))
        );
    }

    public static String generateDiffMsg() {
        String generatedMsg = "";
        if (CrashAssistantConfig.get("modpack_modlist.enabled")) {
            ModListDiff diff = ModListUtils.getDiff();

            String currentUsername = "";
            if (Files.exists(USERNAME_FILE)) {
                try {
                    currentUsername = new String(Files.readAllBytes(USERNAME_FILE));
                } catch (Exception ignored) {
                }
            }
            List<String> modpackCreators = CrashAssistantConfig.getModpackCreators();
            if (modpackCreators.contains(currentUsername) || modpackCreators.isEmpty()) {
                generatedMsg += "Modlist changes beyond the latest successful launch:\n";
            } else {
                generatedMsg += "Modlist changes beyond the modpack:\n";
            }

            if (diff.addedMods().isEmpty() && diff.removedMods().isEmpty()) {
                generatedMsg += "Modpack modlist wasn't modified.\n";
            } else {
                generatedMsg += "Added mods:\n";
                if (diff.addedMods().isEmpty()) {
                    generatedMsg += "Mods weren't added.\n";
                } else {
                    generatedMsg += String.join("\n", diff.addedMods());
                }
                generatedMsg += "\nRemoved mods:\n";
                if (diff.removedMods().isEmpty()) {
                    generatedMsg += "Mods weren't removed.\n";
                } else {
                    generatedMsg += String.join("\n", diff.removedMods());
                }
            }
        }
        return generatedMsg;
    }
}
