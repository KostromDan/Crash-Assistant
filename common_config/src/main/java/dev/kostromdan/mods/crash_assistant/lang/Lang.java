package dev.kostromdan.mods.crash_assistant.lang;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
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
        put("$LANG.gui.upload_all_comment$", null);
        put("$BCC.modpackVersion$", null);
        put("$BCC.modpackName$", null);
    }};
    public HashMap<String, String> lang;
    public static CommentedFileConfig BCCConfig;

    public Lang(HashMap<String, String> lang) {
        this.lang = lang;
    }

    private static String applyPlaceHolders(String value, HashSet<String> placeHoldersSurroundedWithHref) {
        for (Map.Entry<String, String> entry : PlaceHolderToConfigMap.entrySet()) {
            if (!value.contains(entry.getKey())) {
                continue;
            }
            String placeholder = entry.getKey();
            String configValue;
            if (placeholder.startsWith("$LANG.")) {
                configValue = LanguageProvider.get(placeholder.substring(6, placeholder.length() - 1));
            } else if (placeholder.startsWith("$BCC.")) {
                String bccConfigKey = "general." + placeholder.substring(5, placeholder.length() - 1);
                configValue = getBCCValue(bccConfigKey);
            } else {
                configValue = CrashAssistantConfig.get(entry.getValue());
            }
            if (placeHoldersSurroundedWithHref.contains(placeholder)) {
                configValue = "<a href='" + placeholder.substring(1, placeholder.length() - 1) + "'>" + configValue + "</a>";
            }
            String escapedPlaceholder = Pattern.quote(placeholder);
            value = value.replaceAll(escapedPlaceholder, Matcher.quoteReplacement(configValue));
        }
        return value;
    }

    public String get(String key) {
        return get(key, new HashSet<>());
    }

    public String get(String key, HashSet<String> placeHoldersSurroundedWithHref) {
        String value = lang.getOrDefault(key, LanguageProvider.languages.get("en_us").lang.get(key));
        return applyPlaceHolders(value, placeHoldersSurroundedWithHref);
    }

    public static String getBCCValue(String key) {
        try {
            if (BCCConfig == null) {
                Path BCCConfigPath = Paths.get("config","bcc-common.toml");
                if (!Files.exists(BCCConfigPath)) {
                    JarInJarHelper.LOGGER.error("BCC config file not found");
                    return "<BCC config file not found>";
                }
                BCCConfig = CommentedFileConfig.builder(BCCConfigPath).build();
                BCCConfig.load();
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.error("Failed to load config/bcc-common.toml:", e);
            BCCConfig = null;
            return "<BCC config parsing error>";
        }
        String value = BCCConfig.get(key);
        if (value == null) {
            return "<" + key + " not found in BCC config>";
        }
        return value;
    }
}
