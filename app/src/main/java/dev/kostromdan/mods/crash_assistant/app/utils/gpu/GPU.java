package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GPU gpu = (GPU) o;
        return type == gpu.type && Objects.equals(name, gpu.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return "GPU{" +
                "type=" + type +
                ", name='" + name + '\'' +
                '}';
    }

    public static String serializeGPUs(List<GPU> gpus) {
        StringBuilder serializedGPUs = new StringBuilder();

        for (GPU gpu : gpus) {
            serializedGPUs.append(gpu.getType()).append(" : ").append(gpu.getName()).append("\n");
        }
        return serializedGPUs.toString();
    }

    public static List<GPU> deserializeGPUs(String serialisedGPU) {
        List<GPU> gpus = new ArrayList<>();

        // Check if the input string is null or empty
        if (serialisedGPU == null || serialisedGPU.isEmpty()) {
            System.out.println("No GPU information available");
            return gpus;
        }

        // Get all RendererType values for checking
        RendererType[] rendererTypes = RendererType.values();

        for (String line : serialisedGPU.split("\n")) {
            // Trim the line to remove whitespace and leftover carriage returns (\r)
            String trimmedLine = line.trim();

            // Skip empty lines
            if (trimmedLine.isEmpty()) {
                continue;
            }

            // Check if line starts with any of the RendererType values
            boolean isValidGpuLine = false;
            for (RendererType type : rendererTypes) {
                if (trimmedLine.startsWith(type.name())) {
                    isValidGpuLine = true;
                    break;
                }
            }

            if (!isValidGpuLine) {
                // Skip lines that don't start with a valid RendererType
                continue;
            }

            int index = trimmedLine.indexOf(" : ");
            // Check if the line has the expected format
            if (index == -1) {
                System.err.println("Invalid GPU info format: " + trimmedLine);
                continue;
            }

            try {
                RendererType type = Enum.valueOf(RendererType.class, trimmedLine.substring(0, index).toUpperCase());
                String name = trimmedLine.substring(index + 3); // The name is now clean
                gpus.add(new GPU(type, name));
            } catch (Exception e) {
                System.err.println("Error parsing GPU info: " + trimmedLine + " - " + e.getMessage());
            }
        }
        return gpus;
    }
}
