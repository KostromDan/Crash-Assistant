package dev.kostromdan.mods.crash_assistant.app.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlToMarkdown {

    // Pre-compile patterns for performance (faster, less garbage collection)
    private static final Pattern HEADER_PATTERN = Pattern.compile("(?i)<h[1-6].*?>(.*?)</h[1-6]>");
    private static final Pattern LIST_UL_PATTERN = Pattern.compile("(?i)</?ul.*?>");
    private static final Pattern LIST_LI_ITEM_PATTERN = Pattern.compile("(?i)<li.*?>(.*?)</li>");
    private static final Pattern LIST_LI_OPEN_PATTERN = Pattern.compile("(?i)<li.*?>");
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("(?i)<a\\s+href=['\"](.*?)['\"].*?>(.*?)</a>");
    private static final Pattern BOLD_PATTERN = Pattern.compile("(?i)<(b|strong).*?>(.*?)</\\1>");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?i)<(i|em).*?>(.*?)</\\1>");
    private static final Pattern CODE_PATTERN = Pattern.compile("(?i)<code.*?>(.*?)</code>");
    private static final Pattern BREAK_PATTERN = Pattern.compile("(?i)<br\\s*/?>");
    // Matches decorative tags like font, span, div, p but keeps content
    private static final Pattern DECORATIVE_TAG_PATTERN = Pattern.compile("(?i)<(?:font|span|div|p).*?>(.*?)</(?:font|span|div|p)>");
    // Matches any remaining HTML tags to strip them
    private static final Pattern ANY_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * Safe conversion method.
     * Guaranteed not to throw Exceptions on valid String inputs (even if malformed HTML).
     */
    public static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // 1. Normalize newlines
        String text = input.replace("\\n", "\n");
        text = input.replace("\n\n", "\n \n");

        // 2. Apply Regex replacements
        // Note: We use Matcher.quoteReplacement() is not strictly necessary here
        // because our replacement strings are hardcoded constants,
        // but the logic below is the safest standard approach.

        text = HEADER_PATTERN.matcher(text).replaceAll("\n### $1\n");

        text = LIST_UL_PATTERN.matcher(text).replaceAll("");
        text = LIST_LI_ITEM_PATTERN.matcher(text).replaceAll("\n- $1");
        text = LIST_LI_OPEN_PATTERN.matcher(text).replaceAll("\n- "); // Handle unclosed li

        text = ANCHOR_PATTERN.matcher(text).replaceAll("[$2]($1)");

        text = BOLD_PATTERN.matcher(text).replaceAll("**$2**");
        text = ITALIC_PATTERN.matcher(text).replaceAll("*$2*");
        text = CODE_PATTERN.matcher(text).replaceAll("`$1`");

        text = BREAK_PATTERN.matcher(text).replaceAll("\n");

        // Strip decorative tags but keep content
        // We run this in a loop to handle nested tags like <font><font>text</font></font>
        // Simpler approach for stability: just run it once or twice.
        // For total safety against infinite loops, we just run it once here.
        text = DECORATIVE_TAG_PATTERN.matcher(text).replaceAll("$1");

        // Final cleanup of any leftover tags
        text = ANY_TAG_PATTERN.matcher(text).replaceAll("");

        // 3. Decode entities
        text = text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&nbsp;", " ");

        return text.trim();
    }
}