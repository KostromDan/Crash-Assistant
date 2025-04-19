package dev.kostromdan.mods.crash_assistant.common_config.lang;

import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.function.Supplier;

public enum LinksProvider {

    INTEL_CHIP_BUG_FAQ(() -> "https://www.zdnet.com/article/intel-chip-bug-faq-which-pcs-are-affected-how-to-get-the-patch-and-everything-else-you-need-to-know/"),
    AMD_SUPPORT(() -> "https://www.amd.com/en/support"),
    NVIDIA_DRIVERS(() -> "https://www.nvidia.com/en-us/drivers"),
    HOW_FORCE_APP_USE_DISCRETE_GPU(() -> "https://www.xda-developers.com/how-force-app-use-discrete-gpu-windows-11/"),
    AZUL_DOWNLOAD(() -> "https://www.azul.com/downloads/?version=java-21-lts&os=macos&architecture=arm-64-bit&package=jdk#zulu"),
    GLFW_DOWNLOAD(() -> "https://github.com/Frontear/glfw-libs/releases"),
    C6A(() -> "https://modrinth.com/collection/uSfTuDgc"),
    ATL(() -> "https://atlauncher.com/downloads"),
    ADOPTIUM_JDK(() -> "https://adoptium.net/temurin/releases/?package=jdk"),
    RESULTS_OF_MEMORY_DIAGNOSTICS(() -> "https://answers.microsoft.com/en-us/windows/forum/all/how-do-i-see-the-results-of-memory-diagnostic-i/36f9d014-256a-4757-927a-d85ade3b0c09"),

    // Mod links:
    LITHOSTITCHED(() -> "https://www.curseforge.com/minecraft/mc-mods/lithostitched"),
    MODERN_FIX(() -> "https://www.curseforge.com/minecraft/mc-mods/modernfix"),
    FERRITE_CORE(() -> {
        if (PlatformHelp.isForgeBased()) {
            return "https://www.curseforge.com/minecraft/mc-mods/ferritecore";
        }
        return "https://www.curseforge.com/minecraft/mc-mods/ferritecore-fabric";
    }),
    EMBEDDIUM(() -> "https://www.curseforge.com/minecraft/mc-mods/embeddium");


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
