package dev.kostromdan.mods.crash_assistant.common_config.platform;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum PlatformHelp {
    FORGE("https://discord.minecraftforge.net", "Minecraft Forge Discord", "#player-support channel"),
    NEOFORGE("https://discord.neoforged.net", "NeoForge Discord", "#user_support channel"),
    FABRIC("https://discord.gg/v6v4pMv", "Fabric Discord", "#player-support channel"),
    QUILT("https://discord.quiltmc.org/", "QuiltMC Discord", "#player-support channel"),
    UNKNOWN("https://discord.gg/moddedmc", "ModdedMC Discord", "#player-help channel");

    private final String helpLink;
    private final String helpName;
    private final String helpChannel;
    public static PlatformHelp platform = UNKNOWN;
    public static String loaderJarName = "UNDEFINED";
    public static String minecraftVersion = "UNDEFINED";
    public static final String javaVersion = Runtime.version().toString();
    public static String childProcessesPIDs = "UNDEFINED";
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);


    PlatformHelp(String helpLink, String helpName, String helpChannel) {
        this.helpLink = helpLink;
        this.helpName = helpName;
        this.helpChannel = helpChannel;
    }

    public static boolean isLinkDefault() {
        return Objects.equals(CrashAssistantConfig.get("general.help_link"), "CHANGE_ME");
    }

    public static String getActualHelpLink() {
        if (!isLinkDefault()) return CrashAssistantConfig.get("general.help_link");
        return platform.helpLink;
    }

    public static String getActualHelpName() {
        if (!isLinkDefault()) return CrashAssistantConfig.get("text.support_name");
        return platform.helpName;
    }

    public static String getActualHelpChannel() {
        if (!isLinkDefault()) return CrashAssistantConfig.get("text.support_place");
        return platform.helpChannel;
    }

    public static List<String> getOrderedInJarPaths() {
        return switch (platform) {
            case NEOFORGE -> new ArrayList<>() {{
                add("META-INF/neoforge.mods.toml");
                add("META-INF/mods.toml");
                add("fabric.mod.json");
            }};
            case FABRIC, QUILT -> new ArrayList<>() {{
                add("fabric.mod.json");
                add("META-INF/mods.toml");
                add("META-INF/neoforge.mods.toml");
            }};
            case FORGE -> new ArrayList<>() {{
                add("META-INF/mods.toml");
                add("META-INF/neoforge.mods.toml");
                add("fabric.mod.json");
            }};
            default -> new ArrayList<>() {{
                add("META-INF/mods.toml");
                add("META-INF/neoforge.mods.toml");
                add("fabric.mod.json");
            }};
        };
    }

    /**
     * Checks if the current operating system is Linux.
     *
     * @return true if OS is Linux, false otherwise
     */
    public static boolean isLinux() {
        return OS.contains("nux");
    }

    /**
     * Checks if the current operating system is macOS.
     *
     * @return true if OS is macOS, false otherwise
     */
    public static boolean isMacOS() {
        return OS.contains("mac");
    }

    /**
     * Checks if the current operating system is Windows.
     *
     * @return true if OS is Windows, false otherwise
     */
    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isFabricBased() {
        return platform == FABRIC || platform == QUILT;
    }

    public static boolean isForgeBased() {
        return platform == FORGE || platform == NEOFORGE;
    }
}
