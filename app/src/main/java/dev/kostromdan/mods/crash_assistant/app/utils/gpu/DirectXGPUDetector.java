package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

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
        String readMeName = "gpu-detect-jni-README.MD";

        // Create a temporary directory to extract the DLL
        Path tempDir = Paths.get("local", "crash_assistant");
        if (!tempDir.toFile().exists()) {
            tempDir.toFile().mkdirs();
        }
        String fileName = Objects.toString(Boot.parentPID) + "_" + Boot.parentStarted + "_" + libraryName;


        // Extract the DLL to the temporary directory
        Path tempFile = tempDir.resolve(fileName);
        try (InputStream in = DirectXGPUDetector.class.getClassLoader().getResourceAsStream(libraryName)) {
            if (in == null) {
                throw new IOException("Could not find " + libraryName + " in the JAR");
            }
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        Path tempFileReadMe = tempDir.resolve(readMeName);
        try (InputStream in = DirectXGPUDetector.class.getClassLoader().getResourceAsStream(readMeName)) {
            if (in == null) {
                CrashAssistantApp.LOGGER.warn("Could not find " + readMeName + " in the JAR");
            }
            Files.copy(in, tempFileReadMe, StandardCopyOption.REPLACE_EXISTING);
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
