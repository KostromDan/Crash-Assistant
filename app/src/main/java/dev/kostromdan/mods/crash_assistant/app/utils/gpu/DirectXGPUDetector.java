package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

/**
 * Utility class for detecting GPUs and their types using DirectX.
 */
public class DirectXGPUDetector {
    public native static String getSerialisedGPUs();
}
