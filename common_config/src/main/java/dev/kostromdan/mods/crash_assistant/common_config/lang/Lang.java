package dev.kostromdan.mods.crash_assistant.common_config.lang;

import com.electronwill.nightconfig.core.AbstractConfig;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lang {
    public HashMap<String, String> lang;
    public static FileConfig BCCConfig;

    /**
     * Registered transformations keyed by tag name (e.g. "TOLOWER" for &lt;TOLOWER&gt;...&lt;/TOLOWER&gt;).
     * Order is preserved to give deterministic behavior if multiple different tags appear.
     */
    private static final Map<String, Function<String, String>> TEXT_TRANSFORMS = new LinkedHashMap<>();

    /**
     * Register a new text transformation that applies to the content inside &lt;TAG&gt;...&lt;/TAG&gt;.
     * The tag markers themselves are removed from the final output.
     *
     * @param tagName     The tag name without angle brackets (e.g., "TOLOWER").
     * @param transformer A function that transforms the inner text.
     */
    public static void registerTextTransform(String tagName, Function<String, String> transformer) {
        if (tagName == null || tagName.isEmpty() || transformer == null) return;
        TEXT_TRANSFORMS.put(tagName, transformer);
    }

    static {
        registerTextTransform("TOLOWER", s -> s.toLowerCase(Locale.ROOT));
    }

    public Lang(HashMap<String, String> lang) {
        this.lang = lang;
    }

    public String get(String key) {
        return get(key, new HashMap<>());
    }

    public String get(String key, HashMap<String, String> placeHoldersSurroundedWithHref) {
        return get(key, placeHoldersSurroundedWithHref,
                nestedKey -> LanguageProvider.get(nestedKey, placeHoldersSurroundedWithHref));
    }

    public String get(
            String key,
            HashMap<String, String> placeHoldersSurroundedWithHref,
            Function<String, String> languageGetter
    ) {
        String value = lang.getOrDefault(key, LanguageProvider.languages.get("en_us").lang.get(key));
        if (value == null) {
            throw new NullPointerException("Seems like key '" + key + "' is missing in language files");
        }
        return applyPlaceHolders(value, placeHoldersSurroundedWithHref, languageGetter);
    }

    public static String applyPlaceHolders(String value, HashMap<String, String> placeHoldersSurroundedWithHref) {
        return applyPlaceHolders(
                value,
                placeHoldersSurroundedWithHref,
                key -> LanguageProvider.get(key, placeHoldersSurroundedWithHref));
    }

    public static String applyPlaceHolders(
            String value,
            HashMap<String, String> placeHoldersSurroundedWithHref,
            Function<String, String> languageGetter
    ) {
        if (!value.contains("$")) {
            return value;
        }
        value = applyPlaceHolder("$CONFIG.", value, CrashAssistantConfig::get, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$LANG.", value, languageGetter, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$MSG_LANG.", value, LanguageProvider::getMsgLang, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$BCC.", value, Lang::getBCCValue, placeHoldersSurroundedWithHref);
        value = applyPlaceHolder("$LINK.", value, LinksProvider::getLinkByKey, placeHoldersSurroundedWithHref);
        value = applyTextTransforms(value);
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
        Path BCCConfigNewPath = Paths.get("config", "bcc-common.json");
        Path BCCConfigForgePath = Paths.get("config", "bcc-common.toml");
        Path BCCConfigFabricPath = Paths.get("config", "bcc.json");
        try {
            if (BCCConfig == null) {
                if (!Files.exists(BCCConfigForgePath) &&
                        !Files.exists(BCCConfigFabricPath) &&
                        !Files.exists(BCCConfigNewPath)) {
                    JarInJarHelper.LOGGER.error("BCC config file not found");
                    return "<BCC config file not found>";
                }
                boolean newExists = BCCConfigNewPath.toFile().exists();
                boolean forgeExists = BCCConfigForgePath.toFile().exists();
                if (newExists) {
                    BCCConfig = FileConfig.builder(BCCConfigNewPath, JsonFormat.fancyInstance()).build();
                } else if (forgeExists) {
                    BCCConfig = FileConfig.builder(BCCConfigForgePath, TomlFormat.instance()).build();
                } else {
                    BCCConfig = FileConfig.builder(BCCConfigFabricPath, JsonFormat.fancyInstance()).build();
                }
                BCCConfig.load();
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.error("Failed to load BCC config:", e);
            BCCConfig = null;
            return "<BCC config parsing error>";
        }
        key = BCCConfigForgePath.toFile().exists() ? "general." + key : key;
        Object value = BCCConfig.get(key);
        if (value instanceof AbstractConfig) {
            value = ((AbstractConfig) value).get("value");
        }
        if (value == null) {
            return "<" + key + " not found in BCC config>";
        }
        return (String) value;
    }

    /**
     * Applies all registered text transforms to the input string. A transform is triggered by
     * an opening tag &lt;TAG&gt; and the corresponding closing tag &lt;/TAG&gt;. If the closing tag
     * is missing, the transform applies from the end of the opening tag to the end of the string.
     * <p>
     * Only tags that are explicitly registered via {@link #registerTextTransform(String, Function)} are recognized.
     * Regular HTML tags (e.g., &lt;a&gt;, &lt;b&gt;) are ignored unless registered, preventing conflicts.
     * <p>
     * The tag markers themselves are removed from the output; only the transformed inner text remains.
     */
    private static String applyTextTransforms(String input) {
        if (input == null || input.isEmpty() || TEXT_TRANSFORMS.isEmpty()) return input;

        StringBuilder sb = new StringBuilder(input);

        // Continue scanning until no more registered tags are found
        while (true) {
            // Find the earliest occurrence of any registered opening tag
            TagHit next = findNextOpeningTag(sb, 0);
            if (next == null) break;

            String openMarker = next.openMarker;
            String closeMarker = next.closeMarker;

            int openStart = next.openIndex;
            int contentStart = openStart + openMarker.length();

            // Look for the matching closing tag AFTER the opening tag
            int closeStart = indexOf(sb, closeMarker, contentStart);

            int contentEnd;
            int removeEnd; // end index to remove (closing tag included if present)
            if (closeStart >= 0) {
                contentEnd = closeStart;
                removeEnd = closeStart + closeMarker.length();
            } else {
                // No closing tag -> transform until end of string
                contentEnd = sb.length();
                removeEnd = sb.length();
            }

            // Extract, transform, and replace (removing the markers)
            String inner = sb.substring(contentStart, contentEnd);
            Function<String, String> fn = TEXT_TRANSFORMS.get(next.tagName);
            String transformed = (fn != null) ? fn.apply(inner) : inner;

            // Replace: [openStart, contentStart) + [contentStart, contentEnd) + [contentEnd, removeEnd)
            // becomes just transformed
            sb.replace(openStart, removeEnd, transformed);
            // Loop again to catch further tags (including ones created or exposed by replacement)
        }

        return sb.toString();
    }

    private static class TagHit {
        final String tagName;
        final String openMarker;
        final String closeMarker;
        final int openIndex;

        TagHit(String tagName, String openMarker, String closeMarker, int openIndex) {
            this.tagName = tagName;
            this.openMarker = openMarker;
            this.closeMarker = closeMarker;
            this.openIndex = openIndex;
        }
    }

    /**
     * Finds the earliest opening tag occurrence among all registered tags starting from {@code fromIndex}.
     */
    private static TagHit findNextOpeningTag(CharSequence text, int fromIndex) {
        int bestIndex = -1;
        TagHit best = null;

        for (String tagName : TEXT_TRANSFORMS.keySet()) {
            String open = "<" + tagName + ">";
            String close = "</" + tagName + ">";
            int at = indexOf(text, open, fromIndex);
            if (at >= 0 && (bestIndex == -1 || at < bestIndex)) {
                bestIndex = at;
                best = new TagHit(tagName, open, close, at);
            }
        }
        return best;
    }

    /**
     * Safe indexOf for CharSequence.
     */
    private static int indexOf(CharSequence cs, String needle, int fromIndex) {
        if (cs instanceof StringBuilder) {
            return ((StringBuilder) cs).indexOf(needle, fromIndex);
        }
        String hay = cs.toString();
        return hay.indexOf(needle, fromIndex);
    }
}
