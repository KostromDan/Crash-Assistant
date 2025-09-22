package dev.kostromdan.mods.crash_assistant.common_config.lang;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import com.electronwill.nightconfig.toml.TomlFormat;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
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
        return get(key, new HashMap<>());
    }

    public String get(String key, HashMap<String, String> placeHoldersSurroundedWithHref) {
        String value = lang.getOrDefault(key, LanguageProvider.languages.get("en_us").lang.get(key));
        return applyPlaceHolders(value, placeHoldersSurroundedWithHref);
    }

    public static String applyPlaceHolders(String value, HashMap<String, String> placeHoldersSurroundedWithHref) {
        if (!value.contains("$")) {
            return value;
        }
        value = applyPlaceHolder("$CONFIG.", value, CrashAssistantConfig::get, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$LANG.", value, (key) -> LanguageProvider.get(key, placeHoldersSurroundedWithHref), placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$BCC.", value, Lang::getBCCValue, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$LINK.", value, LinksProvider::getLinkByKey, placeHoldersSurroundedWithHref);
        return value;
    }

    private static String applyPlaceHolder(String placeHolderStart, String value, Function<String, String> configGetFunction, HashMap<String, String> placeHoldersSurroundedWithHref) {
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

            if (placeHoldersSurroundedWithHref.containsKey(placeholder) || placeHolderStart.equals("$LINK.")) {
                String placeHolderValue = placeHoldersSurroundedWithHref.get(placeholder);
                if (placeHolderValue == null) {
                    if (placeHolderStart.equals("$LINK.")) {
                        configValue = "<a href='" + configValue + "'>" + configValue + "</a>";
                    } else {
                        configValue = "<a href='" + placeholder.substring(1, placeholder.length() - 1) + "'>" + configValue + "</a>";
                    }
                } else {
                    configValue = "<a href='" + configValue + "'>" + placeHolderValue + "</a>";
                }
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
                boolean forge = BCCConfigForgePath.toFile().exists();
                BCCConfig = FileConfig.builder(
                        forge ? BCCConfigForgePath : BCCConfigFabricPath,
                        forge ? TomlFormat.instance() : JsonFormat.fancyInstance()
                ).build();
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
