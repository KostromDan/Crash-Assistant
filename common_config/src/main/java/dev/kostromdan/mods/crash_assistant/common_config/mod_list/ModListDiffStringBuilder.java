package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ModListDiffStringBuilder {
    public List<ColoredString> sb;

    public ModListDiffStringBuilder() {
        sb = new ArrayList<>();
    }

    public void append(String text, String color, boolean endsWithNewLine) {
        sb.add(new ColoredString(text, color, endsWithNewLine));
    }

    public void append(String text, String color) {
        append(text, color, true);
    }

    public void append(String text, boolean endsWithNewLine) {
        append(text, "", false);
    }

    public void append(String text) {
        append(text, "");
    }

    public String toText() {
        StringBuilder result = new StringBuilder();
        for (ColoredString cs : sb) {
            result.append(cs.getText());
            if (cs.isEndsWithNewLine()) {
                result.append("\n");
            }
        }
        return result.toString().trim();
    }

    public String toHtml() {
        StringBuilder result = new StringBuilder();
        result.append("<html><body style='font-family: Arial; font-size: 12px;white-space: nowrap;'>");

        for (ColoredString cs : sb) {
            result.append("<span" + (cs.getColor().isEmpty() ? "" : " style='color: " + cs.getColor() + ";'") + ">" + cs.getText() + "</span>");
            if (cs.isEndsWithNewLine()) result.append("<br>");
        }
        result.append("</body></html>");
        return result.toString();
    }

    public String toAnsi() {
        return toAnsi(false);
    }

    public String toAnsi(boolean withoutFirstString) {
        return toAnsi(withoutFirstString, CrashAssistantConfig.get("generated_message.ansi_block_pattern", false));
    }

    public String toAnsi(boolean withoutFirstString, String pattern) {
        String prefix = withoutFirstString ? "" : ModListDiff.getFilePrefix();
        String header = withoutFirstString ? "" : ModListDiff.getFirstString(true, false, null);
        return toFormattedString(pattern, prefix, header, !withoutFirstString);
    }

    public String toFormattedString(String pattern, String prefix, String header, boolean skipFirstInSb) {
        StringBuilder contentBuilder = new StringBuilder();

        boolean color_message = CrashAssistantConfig.getBoolean("generated_message.color_message");
        boolean first = skipFirstInSb;
        for (ColoredString cs : sb) {
            if (first) {
                first = false;
                continue;
            }
            if (!cs.getColor().isEmpty() && color_message) {
                contentBuilder.append(Enum.valueOf(AnsiColor.class, cs.getColor().toUpperCase()).getColorPrefix());
                contentBuilder.append(cs.getText());
                contentBuilder.append(AnsiColor.postfix);
            } else {
                contentBuilder.append(cs.getText());
            }
            if (cs.isEndsWithNewLine()) {
                contentBuilder.append("\n");
            }
        }
        String content = contentBuilder.toString().trim();

        return pattern
                .replace("$PREFIX$", prefix)
                .replace("$HEADER$", header)
                .replace("$CONTENT$", content);
    }

    public static class ColoredString {
        private final String text;
        private final String color;
        private final boolean endsWithNewLine;

        public ColoredString(String text, String color, boolean endsWithNewLine) {
            this.text = text;
            this.color = color;
            this.endsWithNewLine = endsWithNewLine;
        }

        public String getText() {
            return text;
        }

        public String getColor() {
            return color;
        }

        public boolean isEndsWithNewLine() {
            return endsWithNewLine;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ColoredString that = (ColoredString) o;
            return endsWithNewLine == that.endsWithNewLine &&
                    Objects.equals(text, that.text) &&
                    Objects.equals(color, that.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, color, endsWithNewLine);
        }

        @Override
        public String toString() {
            return "ColoredString{" +
                    "text='" + text + '\'' +
                    ", color='" + color + '\'' +
                    ", endsWithNewLine=" + endsWithNewLine +
                    '}';
        }
    }
}
