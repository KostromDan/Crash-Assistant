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

    public static TreeSet<String> getCurrentModList() {
        try {
            TreeSet<String> filenames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
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
        return new TreeSet<>();
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
        HashSet<String> currentModList = new HashSet<>(getCurrentModList()); // We want to have O(1) contains()
        HashSet<String> savedModList = getSavedModList();

        return new ModListDiff(
                // Added mods: present in current but not in saved
                currentModList.stream()
                        .filter(mod -> !savedModList.contains(mod))
                        .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))),

                // Removed mods: present in saved but not in current
                savedModList.stream()
                        .filter(mod -> !currentModList.contains(mod))
                        .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)))
        );
    }

    public static String generateDiffMsg() {
        return generateDiffMsg(false);
    }

    public static String getFormattedString(boolean asHtmlWithColor, String text) {
        return getFormattedString(asHtmlWithColor, text, "");
    }

    public static String getFormattedString(boolean asHtmlWithColor, String text, String style) {
        if (asHtmlWithColor) {
            return "<span" + (style.isEmpty() ? "" : " style='" + style + "'") + ">" + text + "</span><br>";
        }
        return text + "\n";
    }

    public static String generateDiffMsg(boolean asHtmlWithColor) {
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
            if (asHtmlWithColor) {
                generatedMsg += "<html><body style='font-family: Arial; font-size: 12px;'>";
            }
            List<String> modpackCreators = CrashAssistantConfig.getModpackCreators();
            if (modpackCreators.contains(currentUsername) || modpackCreators.isEmpty()) {
                generatedMsg += getFormattedString(asHtmlWithColor, "Modlist changes beyond the latest successful launch:");
            } else {
                generatedMsg += getFormattedString(asHtmlWithColor, "Modlist changes beyond the modpack:");
            }

            if (diff.addedMods().isEmpty() && diff.removedMods().isEmpty()) {
                generatedMsg += getFormattedString(asHtmlWithColor, "Modpack modlist wasn't modified.", "color: orange;");
            } else {
                generatedMsg += getFormattedString(asHtmlWithColor, "Added mods:");
                if (diff.addedMods().isEmpty()) {
                    generatedMsg += getFormattedString(asHtmlWithColor, "Mods weren't added.", "color: orange;");
                } else {
                    generatedMsg += diff.addedMods().stream().map(x -> getFormattedString(asHtmlWithColor, x, "color: green;")).collect(Collectors.joining(""));
                }
                generatedMsg += getFormattedString(asHtmlWithColor, "");
                generatedMsg += getFormattedString(asHtmlWithColor, "Removed mods:");
                if (diff.removedMods().isEmpty()) {
                    generatedMsg += getFormattedString(asHtmlWithColor, "Mods weren't removed.", "color: orange;");
                } else {
                    generatedMsg += diff.removedMods().stream().map(x -> getFormattedString(asHtmlWithColor, x, "color: red;")).collect(Collectors.joining(""));
                }
            }
            if (asHtmlWithColor) {
                generatedMsg += "</body></html>";
            }
        }
        return generatedMsg;
    }
}
