package dev.kostromdan.mods.crash_assistant.lang;

import com.electronwill.nightconfig.core.file.FileConfig;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.loading_utils.JarInJarHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lang {
    public static final LinkedHashMap<String, String> PlaceHolderToConfigMap = new LinkedHashMap<>() {{
        put("$SUPPORT_NAME$", "text.support_name");
        put("$MODPACK_NAME$", "text.modpack_name");
        put("$SUPPORT_PLACE$", "text.support_place");
    }};
    public HashMap<String, String> lang;
    public static FileConfig BCCConfig;

    public Lang(HashMap<String, String> lang) {
        this.lang = lang;
    }

    public String get(String key) {
        return get(key, new HashSet<>());
    }

    public String get(String key, HashSet<String> placeHoldersSurroundedWithHref) {
        String value = lang.getOrDefault(key, LanguageProvider.languages.get("en_us").lang.get(key));
        return applyPlaceHolders(value, placeHoldersSurroundedWithHref);
    }

    private static String applyPlaceHolders(String value, HashSet<String> placeHoldersSurroundedWithHref) {
        if (!value.contains("$")) {
            return value;
        }
        for (Map.Entry<String, String> entry : PlaceHolderToConfigMap.entrySet()) {
            if (!value.contains(entry.getKey())) {
                continue;
            }
            String placeholder = entry.getKey();
            String configValue = CrashAssistantConfig.get(entry.getValue());
            value = replacePlaceHolder(value, placeholder, configValue, placeHoldersSurroundedWithHref);
        }
        while (value.contains("$LANG.")) {
            int placeHolderStart = value.indexOf("$LANG.");
            int placeHolderEnd = value.indexOf("$", placeHolderStart + 6) + 1;
            String placeholder = value.substring(placeHolderStart, placeHolderEnd);
            String langKey = placeholder.substring(6, placeholder.length() - 1);
            value = replacePlaceHolder(value, placeholder, LanguageProvider.get(langKey), placeHoldersSurroundedWithHref);
        }
        while (value.contains("$BCC.")) {
            int placeHolderStart = value.indexOf("$BCC.");
            int placeHolderEnd = value.indexOf("$", placeHolderStart + 5) + 1;
            String placeholder = value.substring(placeHolderStart, placeHolderEnd);
            String configKey = placeholder.substring(5, placeholder.length() - 1);
            value = replacePlaceHolder(value, placeholder, getBCCValue(configKey), placeHoldersSurroundedWithHref);
        }
        return value;
    }

    public static String replacePlaceHolder(String value, String placeholder, String configValue, HashSet<String> placeHoldersSurroundedWithHref) {
        if (placeHoldersSurroundedWithHref.contains(placeholder)) {
            configValue = "<a href='" + placeholder.substring(1, placeholder.length() - 1) + "'>" + configValue + "</a>";
        }
        String escapedPlaceholder = Pattern.quote(placeholder);
        value = value.replaceAll(escapedPlaceholder, Matcher.quoteReplacement(configValue));
        return value;
    }

    public static String getBCCValue(String key) {
        Path BCCConfigForgePath = Paths.get("config", "bcc-common.toml");
        Path BCCConfigFabricPath = Paths.get("config", "bcc.json");
        try {
            if (BCCConfig == null) {
                if (!Files.exists(BCCConfigForgePath) && !Files.exists(BCCConfigFabricPath)) {
                    JarInJarHelper.LOGGER.error("BCC config file not found");
                    return "<BCC config file not found>";
                }
                BCCConfig = FileConfig.builder(BCCConfigForgePath.toFile().exists() ? BCCConfigForgePath : BCCConfigFabricPath).build();
                BCCConfig.load();
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.error("Failed to load BCC config:", e);
            BCCConfig = null;
            return "<BCC config parsing error>";
        }
        key = BCCConfigForgePath.toFile().exists() ? "general." + key : key;
        String value = BCCConfig.get(key);
        if (value == null) {
            return "<" + key + " not found in BCC config>";
        }
        return value;
    }
}
