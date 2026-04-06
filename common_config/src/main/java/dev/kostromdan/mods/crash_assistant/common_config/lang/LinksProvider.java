package dev.kostromdan.mods.crash_assistant.common_config.lang;

import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.function.Supplier;

public enum LinksProvider {

    INTEL_CHIP_BUG_FAQ(() -> "https://www.zdnet.com/article/intel-chip-bug-faq-which-pcs-are-affected-how-to-get-the-patch-and-everything-else-you-need-to-know/"),
    AMD_SUPPORT(() -> "https://www.amd.com/en/support"),
    NVIDIA_DRIVERS(() -> "https://www.nvidia.com/Download/index.aspx?lang=en-us"),
    INTEL_DRIVERS(() -> "https://www.intel.com/content/www/us/en/download-center/home.html"),
    HOW_FORCE_APP_USE_DISCRETE_GPU(() -> "https://www.xda-developers.com/how-force-app-use-discrete-gpu-windows-11/"),
    CURSEFORGE_COMMUNITY_GPU_FIX(() -> "https://github.com/CurseForgeCommunity/cf-java-gpu-fix/releases"),
    AZUL_DOWNLOAD(() -> "https://www.azul.com/downloads/?version=java-21-lts&os=macos&architecture=arm-64-bit&package=jdk#zulu"),
    GLFW_DOWNLOAD(() -> "https://github.com/Frontear/glfw-libs/releases"),
    C6A(() -> "https://modrinth.com/collection/uSfTuDgc"),
    ATL(() -> "https://atlauncher.com/downloads"),
    PRISM(() -> "https://prismlauncher.org/download"),
    ADOPTIUM_JDK(() -> "https://adoptium.net/temurin/releases/?package=jdk"),
    OPTIFINE_ALTERNATIVES(() -> "https://prismlauncher.org/wiki/getting-started/install-of-alternatives/"),
    RESULTS_OF_MEMORY_DIAGNOSTICS(() -> "https://answers.microsoft.com/en-us/windows/forum/all/how-do-i-see-the-results-of-memory-diagnostic-i/36f9d014-256a-4757-927a-d85ade3b0c09"),
    MCLOGS_PRIVACY_POLICY(() -> "https://aternos.gmbh/en/mclogs/privacy"),
    VLC(() -> "https://www.videolan.org/vlc/"),
    PRIVACY_POLICY(() -> "PRIVACY_POLICY"),

    JVM_ARGS_GUIDE(() -> "https://github.com/KostromDan/Crash-Assistant/blob/pages/guides/End%20User%20Guides/How%20To%20Manage%20JVM%20Arguments.md"),
    RAM_ALLOCATION_GUIDE(() -> "https://github.com/KostromDan/Crash-Assistant/blob/pages/guides/End%20User%20Guides/How%20To%20Manage%20RAM%20Allocation.md"),
    JAVA_VERSION_GUIDE(() -> "https://github.com/KostromDan/Crash-Assistant/blob/pages/guides/End%20User%20Guides/How%20To%20Manage%20Java%20Version.md"),

    // Mod links:
    CRASH_ASSISTANT(() -> "https://www.curseforge.com/minecraft/mc-mods/crash-assistant"),
    CRASH_ASSISTANT_DISCORD(() -> "https://discord.com/invite/dV8WFrJZK6"),
    LAT_DISCORD(() -> "https://discord.gg/lat"),

    NOT_ENOUGH_IDS(() -> "https://www.curseforge.com/minecraft/mc-mods/notenoughids"),
    ROUGHLY_ENOUGH_IDS(() -> "https://www.curseforge.com/minecraft/mc-mods/reid"),

    LITHOSTITCHED(() -> "https://www.curseforge.com/minecraft/mc-mods/lithostitched"),

    FERRITE_CORE_ISSUES(() -> "https://github.com/malte0811/FerriteCore/issues"),

    MODERN_FIX(() -> "https://www.curseforge.com/minecraft/mc-mods/modernfix"),
    FERRITE_CORE(() -> {
        if (PlatformHelp.isForgeBased()) {
            return "https://www.curseforge.com/minecraft/mc-mods/ferritecore";
        }
        return "https://www.curseforge.com/minecraft/mc-mods/ferritecore-fabric";
    }),

    VINTAGE_FIX(() -> "https://www.curseforge.com/minecraft/mc-mods/vintagefix"),
    CENSORED_ASM(() -> "https://www.curseforge.com/minecraft/mc-mods/lolasm"),

    EMBEDDIUM(() -> "https://www.curseforge.com/minecraft/mc-mods/embeddium"),
    OCULUS(() -> "https://www.curseforge.com/minecraft/mc-mods/oculus"),
    RADIUM(() -> "https://www.curseforge.com/minecraft/mc-mods/radium-reforged"),
    CONNECTOR(() -> "https://www.curseforge.com/minecraft/mc-mods/sinytra-connector"),
    INDIUM(() -> "https://www.curseforge.com/minecraft/mc-mods/indium"),
    FEATURE_RECYCLER(() -> "https://www.curseforge.com/minecraft/mc-mods/feature-recycler"),
    MIXIN_EXTRAS_NEO(() -> "https://www.curseforge.com/minecraft/mc-mods/mixin-extras-neoforge-on-forge-fix");


    private final Supplier<String> linkSupplier;

    LinksProvider(Supplier<String> linkSupplier) {
        this.linkSupplier = linkSupplier;
    }

    public String getLink() {
        return linkSupplier.get();
    }

    public static String getLinkByKey(String enumKey) {
        return Enum.valueOf(LinksProvider.class, enumKey).getLink();
    }
}
