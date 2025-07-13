package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Utility class for detecting GPUs and their types using DirectX.
 */
public class DirectXGPUDetector {
    static {
        try {
            loadNativeLibraryFromJar();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library: " + e.getMessage(), e);
        }
    }

    private native static byte[] getNativeSerialisedGPUs();

    public static String getSerialisedGPUs() {
        byte[] bytes = getNativeSerialisedGPUs();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void loadNativeLibraryFromJar() throws IOException {
        String libraryName = "gpu-detect-jni.dll";

        // Create a temporary directory to extract the DLL
        Path tempDir = Files.createTempDirectory("gpu-detect-jni");
        tempDir.toFile().deleteOnExit();

        // Extract the DLL to the temporary directory
        Path tempFile = tempDir.resolve(libraryName);
        try (InputStream in = DirectXGPUDetector.class.getClassLoader().getResourceAsStream(libraryName)) {
            if (in == null) {
                throw new IOException("Could not find " + libraryName + " in the JAR");
            }
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Load the DLL from the temporary location
        System.load(tempFile.toAbsolutePath().toString());
    }

    public static void main(String[] args) throws Exception { // Test
        String serialized = getSerialisedGPUs();
        System.out.println(serialized);
        List<GPU> gpus = GPU.deserializeGPUs(serialized);
        System.out.println(gpus);
    }
}
