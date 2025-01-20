package dev.kostromdan.mods.crash_assistant.platform;

import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;

import java.util.Objects;

public enum PlatformHelp {
    FORGE("https://discord.minecraftforge.net", "Minecraft Forge Discord", "#player-support"),
    NEOFORGE("https://discord.neoforged.net", "NeoForge Discord", "#user_support"),
    FABRIC("https://discord.gg/v6v4pMv", "Fabric Discord", "#player-support"),
    QUILT("https://discord.quiltmc.org/", "QuiltMC Discord", "#quilt-player-support"),
    UNCKNOWN("https://discord.gg/moddedmc", "ModdedMC Discord", "#player-help");

    private final String helpLink;
    private final String helpName;
    private final String helpChannel;
    public static PlatformHelp platform = UNCKNOWN;

    PlatformHelp(String helpLink, String helpName, String helpChannel) {
        this.helpLink = helpLink;
        this.helpName = helpName;
        this.helpChannel = helpChannel;
    }

    public String getHelpLink() {
        return helpLink;
    }

    public String getHelpName() {
        return helpName;
    }

    public String getHelpChannel() {
        return helpChannel;
    }

    public static boolean isLinkDefault() {
        return Objects.equals(CrashAssistantConfig.get("general.help_link"), "CHANGE_ME");
    }

    public static String getActualHelpLink() {
        if (!isLinkDefault()) return CrashAssistantConfig.get("general.help_link");
        return platform.getHelpLink();
    }
    public static String getActualHelpName() {
        if (!isLinkDefault()) return CrashAssistantConfig.get("text.support_name");
        return platform.getHelpName();
    }
    public static String getActualHelpChannel() {
        if (!isLinkDefault()) return CrashAssistantConfig.get("text.support_place");
        return platform.getHelpChannel();
    }
}
