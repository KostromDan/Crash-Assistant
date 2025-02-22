package dev.kostromdan.mods.crash_assistant.platform;

import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;

import java.util.ArrayList;
import java.util.List;
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
        switch (platform) {
            case NEOFORGE:
                new ArrayList<>() {{
                    add("META-INF/neoforge.mods.toml");
                    add("META-INF/mods.toml");
                    add("fabric.mod.json");
                }};
            case FABRIC, QUILT:
                new ArrayList<>() {{
                    add("fabric.mod.json");
                    add("META-INF/mods.toml");
                    add("META-INF/neoforge.mods.toml");
                }};
            case FORGE:
                return new ArrayList<>() {{
                    add("META-INF/mods.toml");
                    add("META-INF/neoforge.mods.toml");
                    add("fabric.mod.json");
                }};
            default:
                return new ArrayList<>() {{
                    add("META-INF/mods.toml");
                    add("META-INF/neoforge.mods.toml");
                    add("fabric.mod.json");
                }};
        }
    }
}
