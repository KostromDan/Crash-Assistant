package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import java.util.ArrayList;
import java.util.List;

/**
 * Record representing a GPU with its type and name.
 */
public record GPU(RendererType type, String name) {
    public static String serialiseGPUs(List<GPU> gpus) {
        List<String> gpuStrings = new ArrayList<>();
        for (GPU gpu : gpus) {
            gpuStrings.add(gpu.type() + " : " + gpu.name());
        }
        return String.join("\n", gpuStrings);
    }

    public static List<GPU> deserialiseGPUs(String serialisedGPU) {
        List<GPU> gpus = new ArrayList<>();
        for (String line : serialisedGPU.split("\n")) {
            int index = line.indexOf(" : ");
            RendererType type = Enum.valueOf(RendererType.class, line.substring(0, index).toUpperCase());
            String name = line.substring(index + 3);
            gpus.add(new GPU(type, name));
        }
        return gpus;
    }
}

