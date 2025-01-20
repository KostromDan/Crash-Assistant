package dev.kostromdan.mods.crash_assistant.lang;

import com.electronwill.nightconfig.core.file.FileConfig;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.platform.PlatformHelp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lang {
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

    public static String applyPlaceHolders(String value, HashSet<String> placeHoldersSurroundedWithHref) {
        if (!value.contains("$")) {
            return value;
        }
        value = applyPlaceHolder("$CONFIG.", value, CrashAssistantConfig::get, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$LANG.", value, LanguageProvider::get, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$BCC.", value, Lang::getBCCValue, placeHoldersSurroundedWithHref);
        return value;
    }

    private static String applyPlaceHolder(String placeHolderStart, String value, Function<String, String> configGetFunction, HashSet<String> placeHoldersSurroundedWithHref) {
        while (value.contains(placeHolderStart)) {
            int placeHolderStartLength = placeHolderStart.length();
            int placeHolderStartIndex = value.indexOf(placeHolderStart);
            int placeHolderEndIndex = value.indexOf("$", placeHolderStartIndex + placeHolderStartLength) + 1;
            String placeholder = value.substring(placeHolderStartIndex, placeHolderEndIndex);
            String configKey = placeholder.substring(placeHolderStartLength, placeholder.length() - 1);
            String configValue;
            if (placeHolderStart.equals("$CONFIG.") && (configKey.equals("text.support_place") || configKey.equals("text.support_name"))) {
                configValue = configKey.equals("text.support_place") ? PlatformHelp.getActualHelpChannel() : PlatformHelp.getActualHelpName();
            } else {
                configValue = configGetFunction.apply(configKey);
            }

            if (placeHoldersSurroundedWithHref.contains(placeholder)) {
                configValue = "<a href='" + placeholder.substring(1, placeholder.length() - 1) + "'>" + configValue + "</a>";
            }
            value = value.replaceAll(Pattern.quote(placeholder), Matcher.quoteReplacement(configValue));
        }
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
