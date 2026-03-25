package dev.kostromdan.mods.crash_assistant.common_config.utils;

import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.VersionUtils;

public class ModVersionsHelper {
    public static final VersionRange versionRange;

    static {
        if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.minecraftVersion, "1.18.2")) versionRange = VersionRange.V_1_18_2__MODERN;
        else if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.minecraftVersion, "1.17")) versionRange = VersionRange.V_1_17__1_18_1;
        else if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.minecraftVersion, "1.13")) versionRange = VersionRange.V_1_13__1_16_5;
        else if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.minecraftVersion, "1.12")) versionRange = VersionRange.V_1_12_2;
        else if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.minecraftVersion, "1.8")) versionRange = VersionRange.V_1_8__1_11_2;
        else if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.minecraftVersion, "1.7")) versionRange = VersionRange.V_1_7_10;
        else if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.minecraftVersion, "1.6")) versionRange = VersionRange.V_1_6_4;
        else throw new IllegalStateException("Unknown Minecraft version: " + PlatformHelp.minecraftVersion);
    }


    public enum VersionRange {
        V_1_6_4,
        V_1_7_10,
        V_1_8__1_11_2,
        V_1_12_2,
        V_1_13__1_16_5,
        V_1_17__1_18_1,
        V_1_18_2__MODERN

    }
}
