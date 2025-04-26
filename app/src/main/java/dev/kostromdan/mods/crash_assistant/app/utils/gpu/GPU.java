package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import java.util.ArrayList;
import java.util.List;

/**
 * Class representing a GPU with its type and name.
 */
public class GPU {
    private final RendererType type;
    private final String name;

    public GPU(RendererType type, String name) {
        this.type = type;
        this.name = name;
    }

    public RendererType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public static String serializeGPUs(List<GPU> gpus) {
        List<String> gpuStrings = new ArrayList<>();
        for (GPU gpu : gpus) {
            gpuStrings.add(gpu.getType() + " : " + gpu.getName());
        }
        return String.join("\n", gpuStrings);
    }

    public static List<GPU> deserializeGPUs(String serialisedGPU) {
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

