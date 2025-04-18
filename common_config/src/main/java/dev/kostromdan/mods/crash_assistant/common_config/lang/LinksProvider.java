package dev.kostromdan.mods.crash_assistant.common_config.lang;

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

    // Mod links:
    LITHOSTITCHED(() -> "https://www.curseforge.com/minecraft/mc-mods/lithostitched");


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
