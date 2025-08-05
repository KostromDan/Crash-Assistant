package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

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

        String currentProcessData = Objects.toString(Boot.parentPID) + "_" + Boot.parentStarted;
        String outputFileName = currentProcessData + "_gpu_detect_jni.dll";
        Path localFolder = Paths.get("local", "crash_assistant");
        Files.createDirectories(localFolder);
        Path dllOutputFile = localFolder.resolve(outputFileName);
        try (InputStream in = DirectXGPUDetector.class.getClassLoader().getResourceAsStream(libraryName)) {
            if (in == null) {
                throw new IOException("Could not find " + libraryName + " in the JAR");
            }
            Files.copy(in, dllOutputFile, StandardCopyOption.REPLACE_EXISTING);
        }
        System.load(dllOutputFile.toAbsolutePath().toString());
    }

    public static void main(String[] args) throws Exception { // Test
        String serialized = getSerialisedGPUs();
        System.out.println(serialized);
        List<GPU> gpus = GPU.deserializeGPUs(serialized);
        System.out.println(gpus);
    }
}
