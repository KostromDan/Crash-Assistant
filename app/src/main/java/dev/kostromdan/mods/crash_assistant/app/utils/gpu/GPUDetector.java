package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ClassExistenceChecker;

import java.util.List;

/**
 * Main GPU detection utility that tries different detection methods.
 * First attempts to use Vulkan for GPU detection, and if that fails,
 * falls back to DirectX.
 */
public class GPUDetector {

    /**
     * Returns a serialized string representation of detected GPUs.
     * First tries to use Vulkan, and if that fails, falls back to DirectX.
     *
     * @return a serialized string representation of detected GPUs
     */
    public static String getSerialisedGPUs() {
        String serialisedGPUs = "";
        boolean vulkanSuccess = false;

        // Try Vulkan first
        try {
            // Check if Vulkan and lwjglNatives are available
            if (ClassExistenceChecker.classExists("org.lwjgl.vulkan.VK10") && Boot.lwjglNatives != null) {
                String vulkanResult = VulkanGPUDetector.getSerialisedGPUs();

                // Check if the result is not empty
                List<GPU> gpus = GPU.deserializeGPUs(vulkanResult);
                if (gpus != null && !gpus.isEmpty()) {
                    serialisedGPUs += "Successfully detected GPUs using Vulkan:\n";
                    serialisedGPUs += vulkanResult;
                    vulkanSuccess = true;
                } else {
                    serialisedGPUs += "Vulkan detection returned empty result, falling back to DirectX\n";
                }
            } else {
                serialisedGPUs += "Vulkan classes or lwjglNatives not found, falling back to DirectX\n";
            }
        } catch (Exception e) {
            serialisedGPUs += "Error during Vulkan GPU detection: " + e.getMessage() + ", falling back to DirectX\n";
        }

        // If Vulkan failed, try DirectX
        if (!vulkanSuccess) {
            if (PlatformHelp.isWindows()) {
                try {
                    String directXResult = DirectXGPUDetector.getSerialisedGPUs();
                    serialisedGPUs += "Successfully detected GPUs using DirectX:\n";
                    serialisedGPUs += directXResult;
                } catch (Exception e) {
                    serialisedGPUs += "Error during DirectX GPU detection: " + e.getMessage() + "\n";
                }
            } else {
                serialisedGPUs += "DirectX GPU detection is not supported on non-Windows platforms\n" +
                        "If you want Minecraft running of integrated GPU while dedicated GPU exists warning feature work on your pc,\n" +
                        "pls install our addon with Vulkan lib (Crash Assistant cross platform integrated GPU detection addon).";
            }
        }

        return serialisedGPUs;
    }

    public static void main(String[] args) { // Test
        String serialized = getSerialisedGPUs();
        System.out.println(serialized);
        List<GPU> gpus = GPU.deserializeGPUs(serialized);
        System.out.println(gpus);
    }
}
