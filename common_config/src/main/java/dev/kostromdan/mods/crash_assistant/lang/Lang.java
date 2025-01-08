package dev.kostromdan.mods.crash_assistant.lang;

import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Lang {
    public static final HashMap<String, String> PlaceHolderToConfigMap = new HashMap<>() {{
        put("$SUPPORT_NAME$", "text.support_name");
        put("$MODPACK_NAME$", "text.modpack_name");
        put("$SUPPORT_PLACE$", "text.support_place");
    }};
    public HashMap<String, String> lang;

    public Lang(HashMap<String, String> lang) {
        this.lang = lang;
    }

    private static String applyPlaceHolders(String value) {
        for (Map.Entry<String, String> entry : PlaceHolderToConfigMap.entrySet()) {
            if (!value.contains(entry.getKey())) {
                continue;
            }
            String placeholder = entry.getKey();
            String configValue = CrashAssistantConfig.get(entry.getValue());
            String escapedPlaceholder = Pattern.quote(placeholder);

            value = value.replaceAll(escapedPlaceholder, configValue);
        }
        return value;
    }

    public String get(String key) {
        String value = lang.getOrDefault(key, LanguageProvider.languages.get("en_us").lang.get(key));
        return applyPlaceHolders(value);
    }
}
