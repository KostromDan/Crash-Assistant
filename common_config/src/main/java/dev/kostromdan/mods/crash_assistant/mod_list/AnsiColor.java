package dev.kostromdan.mods.crash_assistant.mod_list;

public enum AnsiColor {
    RED("\u001B[2;31m"),
    GREEN("\u001B[2;32m"),
    BLUE("\u001B[2;34m");


    private final String colorPrefix;
    public static final String postfix  = "\u001B[0m";

    AnsiColor(String colorPrefix) {
        this.colorPrefix = colorPrefix;
    }

    public String getColorPrefix() {
        return colorPrefix;
    }
}
