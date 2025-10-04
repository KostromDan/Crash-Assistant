package dev.kostromdan.mods.crash_assistant.common_config.platform;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.VersionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum PlatformHelp {
    //    FORGE("https://discord.minecraftforge.net", "Minecraft Forge Discord", "#player-support channel"),
    NEOFORGE("https://discord.neoforged.net", "NeoForge Discord", "#user_support channel"),
    FABRIC("https://discord.gg/v6v4pMv", "Fabric Discord", "#player-support channel"),
    QUILT("https://discord.quiltmc.org/", "QuiltMC Discord", "#player-support channel"),

    UNKNOWN("https://discord.gg/moddedmc", "ModdedMC Discord", "#player-help channel"),
    CLEANROOM("https://discord.gg/sgQxDJdrnY", "Cleanroom Discord", "#lobby channel"),
    LEGACY_MODDING("https://discord.com/invite/AJxEFs6YBN", "Legacy Modding Discord", "#modding-help forums"),
    FORGE(CLEANROOM);

    private final String helpLink;
    private final String helpName;
    private final String helpChannel;
    public static PlatformHelp platform = UNKNOWN;
    public static boolean modLoadedWithConnector = false;
    public static String loaderJarName = "UNDEFINED";
    public static String minecraftVersion = "UNDEFINED";
    public static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    public static final String javaVersion = ProcessHelper.getJavaVersion();
    public static String childProcessesPIDs = "UNDEFINED";


    PlatformHelp(String helpLink, String helpName, String helpChannel) {
        this.helpLink = helpLink;
        this.helpName = helpName;
        this.helpChannel = helpChannel;
    }

    PlatformHelp(PlatformHelp fromPlatform) {
        this.helpLink = fromPlatform.helpLink;
        this.helpName = fromPlatform.helpName;
        this.helpChannel = fromPlatform.helpChannel;
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
        List<String> paths = new ArrayList<>();
        switch (platform) {
            case NEOFORGE:
                paths.add("META-INF/neoforge.mods.toml");
                paths.add("META-INF/mods.toml");
                paths.add("fabric.mod.json");
                break;

            case FABRIC:
            case QUILT:
                paths.add("fabric.mod.json");
                paths.add("META-INF/mods.toml");
                paths.add("META-INF/neoforge.mods.toml");
                break;

            case FORGE:
                if (VersionUtils.isLowerThanOrEqual(minecraftVersion, "1.12.2")) {
                    paths.add("mcmod.info");
                }
                paths.add("META-INF/mods.toml");
                paths.add("META-INF/neoforge.mods.toml");
                paths.add("fabric.mod.json");
                break;

            default:
                paths.add("META-INF/mods.toml");
                paths.add("META-INF/neoforge.mods.toml");
                paths.add("fabric.mod.json");
                paths.add("mcmod.info");
                break;
        }
        return paths;
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

    /**
     * Parses jdeps --version output and extracts major version number.
     * Example input: "21" or "21.0.8" or "21.0.8+8" or "22-ea" or "22-ea+27" or "1.8.0_202"
     * 
     * @param jdepsVersionOutput the output from jdeps --version command
     * @return major version number, or -1 if parsing fails
     */
    public static int parseJdkMajorVersion(String jdepsVersionOutput) {
        if (jdepsVersionOutput == null || jdepsVersionOutput.trim().isEmpty()) {
            return -1;
        }

        String version = jdepsVersionOutput.trim();

        // Handle old format like "1.8.0_202"
        if (version.startsWith("1.")) {
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }

        // Handle new format like "21", "21.0.8", "21.0.8+8", "22-ea", "22-ea+27"
        // Extract the first number before any dot, dash, or plus
        StringBuilder majorVersionStr = new StringBuilder();
        for (char c : version.toCharArray()) {
            if (Character.isDigit(c)) {
                majorVersionStr.append(c);
            } else if (c == '.' || c == '-' || c == '+') {
                break;
            } else {
                // Skip non-digit characters at the beginning
                if (majorVersionStr.length() == 0) {
                    continue;
                } else {
                    break;
                }
            }
        }

        if (majorVersionStr.length() > 0) {
            try {
                return Integer.parseInt(majorVersionStr.toString());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1;
    }

    /**
     * Gets the major version from the current Java runtime.
     * 
     * @return current major version number
     */
    public static int getCurrentJdkMajorVersion() {
        return parseJdkMajorVersion(javaVersion);
    }

    /**
     * Compares if the parsed JDK version is sufficient compared to current runtime.
     * 
     * @param jdepsVersionOutput the output from jdeps --version command
     * @return false if parsed version is lower than current major version, true otherwise
     */
    public static boolean isJdkVersionSufficient(String jdepsVersionOutput) {
        int parsedMajorVersion = parseJdkMajorVersion(jdepsVersionOutput);
        int currentMajorVersion = getCurrentJdkMajorVersion();

        if (parsedMajorVersion == -1 || currentMajorVersion == -1) {
            return true; // If parsing fails, assume it's sufficient to avoid blocking
        }

        return parsedMajorVersion >= currentMajorVersion;
    }
}
