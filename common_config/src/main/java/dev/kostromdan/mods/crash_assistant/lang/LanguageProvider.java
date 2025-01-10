package dev.kostromdan.mods.crash_assistant.lang;

import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.loading_utils.JarInJarHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class LanguageProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    public static Path OPTIONS_PATH = Paths.get("options.txt");
    public static Path LANG_PATH = Paths.get("config", "crash_assistant", "lang");
    public static HashMap<String, Lang> languages = new HashMap<>();
    public static String currentLangName;

    static {
        updateLang();
        unzipAndUpdateLangFiles();
    }

    public static String get(String key) {
        return languages.getOrDefault(currentLangName, languages.get("en_us")).get(key);
    }

    public static String get(String key,HashSet<String> placeHoldersSurroundedWithHref) {
        return languages.getOrDefault(currentLangName, languages.get("en_us")).get(key, placeHoldersSurroundedWithHref);
    }

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
                    return line.split(":", 2)[1].trim();
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error while reading " + OPTIONS_PATH.getFileName() + " file. Default lang will be used.");
        }

        return CrashAssistantConfig.get("general.default_lang");
    }

    @SuppressWarnings("unchecked")
    public static void unzipAndUpdateLangFiles() {
        CrashAssistantConfig.executeWithLock(() -> {
            LANG_PATH.toFile().mkdirs();
            HashSet<String> langFilesInJarNames = new HashSet<>();
            langFilesInJarNames.add("lang/en_us.json");
            langFilesInJarNames.add("lang/ru_ru.json");
            langFilesInJarNames.add("lang/zh_cn.json");
            langFilesInJarNames.add("lang/README.md");
            HashMap<String, HashMap<String, String>> jarLangFiles = new HashMap<>();
            for (String langFile : langFilesInJarNames) {
                if (langFile.endsWith("/")) {
                    continue;
                }
                String langFileName = langFile.split("/")[1];
                if (!langFile.endsWith(".json")) {
                    JarInJarHelper.unzipFromJar(langFile, LANG_PATH.resolve(langFileName));
                    continue;
                }
                jarLangFiles.put(langFileName.split("\\.json")[0], JarInJarHelper.readJsonFromJar(langFile));
            }

            HashSet<Path> langFilesInConfigNames = getLangFilesInConfigPaths();
            HashMap<String, HashMap<String, String>> configLangFiles = new HashMap<>();
            for (Path path : langFilesInConfigNames) {
                configLangFiles.put(path.getFileName().toString().split("\\.json")[0], JarInJarHelper.readJsonFromFile(path));
            }

            HashSet<String> allLanguages = new HashSet<>();
            allLanguages.addAll(configLangFiles.keySet());
            allLanguages.addAll(jarLangFiles.keySet());


            Lang en_us_jar = new Lang(jarLangFiles.get("en_us"));
            for (String langName : allLanguages) {
                Lang jar = new Lang(jarLangFiles.getOrDefault(langName, new HashMap<>()));
                final Lang unmodified_jar = new Lang((HashMap<String, String>) jar.lang.clone());
                Lang config = new Lang(configLangFiles.getOrDefault(langName, new HashMap<>()));
                config.lang.forEach((key, value) -> {
                    if (en_us_jar.lang.containsKey(key) && !Objects.equals(value, "$DEFAULT")) {
                        jar.lang.put(key, value);
                    }
                });
                languages.put(langName, jar);
                HashMap<String, String> outputJson = new HashMap<>();

                en_us_jar.lang.forEach((key, value) -> {
                    String outputValue;
                    if (Objects.equals(config.lang.get(key), "$DEFAULT")) {
                        outputValue = "$DEFAULT";
                    } else if (Objects.equals(unmodified_jar.lang.get(key), config.lang.get(key))) {
                        outputValue = "$DEFAULT";
                    } else if (!unmodified_jar.lang.containsKey(key) && Objects.equals(value, config.lang.get(key))) {
                        outputValue = "$DEFAULT";
                    } else outputValue = config.lang.getOrDefault(key, "$DEFAULT");
                    outputJson.put(key, outputValue);
                });
                JarInJarHelper.writeJsonToFile(outputJson, LANG_PATH.resolve(langName + ".json"));
            }
        });
    }

    public static HashSet<Path> getLangFilesInConfigPaths() {
        HashSet<Path> langFilesInConfigNames = new HashSet<>();
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
}
