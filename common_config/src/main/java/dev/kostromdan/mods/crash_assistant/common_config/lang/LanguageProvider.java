package dev.kostromdan.mods.crash_assistant.common_config.lang;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import org.apache.commons.jexl3.annotations.NoJexl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class LanguageProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    @NoJexl
    public static Path OPTIONS_PATH = Paths.get("options.txt");
    @NoJexl
    public static Path LANG_PATH = Paths.get("config", "crash_assistant", "crash_assistant_localization_overrides");
    @NoJexl
    public static HashMap<String, Lang> languages = new HashMap<>();
    public static String currentLangName;
    public static String msgLangName;

    static {
        migrateLangDirectory();
        updateLang();
        unzipAndUpdateLangFiles();
    }

    @NoJexl
    private static void migrateLangDirectory() {
        Path oldLangPath = Paths.get("config", "crash_assistant", "lang");
        if (Files.exists(oldLangPath) && Files.isDirectory(oldLangPath)) {
            try {
                LANG_PATH.toFile().mkdirs();
                Files.list(oldLangPath).forEach(file -> {
                    try {
                        Files.move(file, LANG_PATH.resolve(file.getFileName()));
                    } catch (FileAlreadyExistsException e) {
                        LOGGER.warn("Tried to migrate file {}, but seems like it's already migrated, removing from the old folder...", file);
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException ignored) {
                        }
                    } catch (IOException e) {
                        LOGGER.error("Failed to move file: " + file, e);
                    }
                });
                Files.delete(oldLangPath);
            } catch (IOException e) {
                LOGGER.error("Failed to migrate lang directory", e);
            }
        }
    }

    public static String get(String key) {
        return languages.getOrDefault(currentLangName, languages.get("en_us")).get(key);
    }

    public static String getMsgLang(String key) {
        if (msgLangName == null) {
            msgLangName = CrashAssistantConfig.get("generated_message.generated_msg_lang");
        }
        return languages.getOrDefault(msgLangName, languages.get("en_us")).get(key);
    }

    public static String get(String key, HashMap<String, String> placeHoldersSurroundedWithHref) {
        return languages.getOrDefault(currentLangName, languages.get("en_us")).get(key, placeHoldersSurroundedWithHref);
    }

    @NoJexl
    public static void updateLang() {
        currentLangName = getCurrentLang();
    }

    public static String getCurrentLang() {
        if (!Files.exists(OPTIONS_PATH)) {
            return CrashAssistantConfig.get("general.default_lang");
        }

        try {
            List<String> lines = Files.readAllLines(OPTIONS_PATH);
            for (String line : lines) {
                if (line.startsWith("lang:")) {
                    return line.split(":", 2)[1].trim().toLowerCase();
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error while reading " + OPTIONS_PATH.getFileName() + " file. Default lang will be used.");
        }

        return CrashAssistantConfig.get("general.default_lang");
    }

    @NoJexl
    @SuppressWarnings("unchecked")
    public static void unzipAndUpdateLangFiles() {
        CrashAssistantConfig.executeWithLock(() -> {
            boolean generateLocalizationFolderWithReadme = CrashAssistantConfig.get("general.generate_localization_overrides_folder_with_readme");
            String priorityLangForOverrides = CrashAssistantConfig.get("general.priority_lang_for_overrides");

            if (generateLocalizationFolderWithReadme) {
                LANG_PATH.toFile().mkdirs();
            }

            HashSet<String> langFilesInJarNames = new HashSet<>();
            langFilesInJarNames.add("crash_assistant_localization/de_de.json");
            langFilesInJarNames.add("crash_assistant_localization/en_us.json");
            langFilesInJarNames.add("crash_assistant_localization/ru_ru.json");
            langFilesInJarNames.add("crash_assistant_localization/zh_cn.json");
            langFilesInJarNames.add("crash_assistant_localization/es_es.json");
            langFilesInJarNames.add("crash_assistant_localization/it_it.json");
            langFilesInJarNames.add("crash_assistant_localization/pt_br.json");
            langFilesInJarNames.add("crash_assistant_localization/ms_my.json");
            langFilesInJarNames.add("crash_assistant_localization/zlm_arab.json");
            langFilesInJarNames.add("crash_assistant_localization/ja_jp.json");
            langFilesInJarNames.add("crash_assistant_localization/README.md");

            HashMap<String, HashMap<String, String>> jarLangFiles = new HashMap<>();
            HashMap<String, HashMap<String, String>> configLangFiles = new HashMap<>();

            for (String langFile : langFilesInJarNames) {
                if (langFile.endsWith("/")) {
                    continue;
                }
                String langFileName = langFile.split("/")[1];
                if (!langFile.endsWith(".json")) {
                    if (generateLocalizationFolderWithReadme) {
                        JarInJarHelper.unzipFromJar(langFile, LANG_PATH.resolve(langFileName));
                    }
                    continue;
                }
                jarLangFiles.put(langFileName.split("\\.json")[0], JarInJarHelper.readJsonFromJar(langFile));
            }

            Lang en_usFromFar = new Lang(jarLangFiles.get("en_us"));

            HashSet<Path> langFilesInConfigNames = getLangFilesInConfigPaths();
            HashSet<Path> langFilesToRemove = new HashSet<>();
            for (Path path : langFilesInConfigNames) {
                HashMap<String, String> lang = JarInJarHelper.readJsonFromFile(path);

                if (lang.isEmpty()) {
                    langFilesToRemove.add(path);
                    continue;
                }

                String langName = path.getFileName().toString().split("\\.json")[0];
                HashSet<String> keysToRemove = new HashSet<>();

                for (Map.Entry<String, String> entry : lang.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (!en_usFromFar.lang.containsKey(key) && !key.startsWith("custom.")) {
                        keysToRemove.add(key);
                        continue;
                    }
                    if (value.equals("$DEFAULT")) {
                        keysToRemove.add(key);
                        continue;
                    }
                    if (jarLangFiles.containsKey(langName) && jarLangFiles.get(langName).containsKey(key) && jarLangFiles.get(langName).get(key).equals(value)) {
                        keysToRemove.add(key);
                        continue;
                    }
                }
                for (String key : keysToRemove) {
                    lang.remove(key);
                }
                if (lang.isEmpty()) {
                    langFilesToRemove.add(path);
                    continue;
                }
                if (!keysToRemove.isEmpty()) {
                    JarInJarHelper.writeJsonToFile(lang, path);
                }
                configLangFiles.put(langName, lang);
            }

            for (Path path : langFilesToRemove) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                }
            }

            HashSet<String> allLanguages = new HashSet<>();
            allLanguages.addAll(configLangFiles.keySet());
            allLanguages.addAll(jarLangFiles.keySet());

            HashMap<String, String> FirstPriorityLangForOverrides = configLangFiles.getOrDefault(priorityLangForOverrides, new HashMap<>());
            for (String langName : allLanguages) {
                HashMap<String, String> lang = configLangFiles.getOrDefault(langName, new HashMap<>());
                for (Map.Entry<String, String> entry : FirstPriorityLangForOverrides.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (!lang.containsKey(key)) {
                        lang.put(key, value);
                    }
                }
                for (Map.Entry<String, String> entry : jarLangFiles.getOrDefault(langName, new HashMap<>()).entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (!lang.containsKey(key)) {
                        lang.put(key, value);
                    }
                }
                languages.put(langName, new Lang(lang));
            }
        });
    }

    @NoJexl
    public static HashSet<Path> getLangFilesInConfigPaths() {
        HashSet<Path> langFilesInConfigNames = new HashSet<>();
        if (!Files.exists(LANG_PATH)) {
            return langFilesInConfigNames;
        }
        try {
            Files.list(LANG_PATH).forEach(path -> {
                if (path.getFileName().toString().endsWith(".json")) {
                    langFilesInConfigNames.add(path);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to list lang files in " + LANG_PATH.getFileName().toString(), e);
        }
        return langFilesInConfigNames;
    }

    public static Function<String, String> getLangFunction(boolean forMsg) {
        return forMsg ? LanguageProvider::getMsgLang : LanguageProvider::get;
    }
}
