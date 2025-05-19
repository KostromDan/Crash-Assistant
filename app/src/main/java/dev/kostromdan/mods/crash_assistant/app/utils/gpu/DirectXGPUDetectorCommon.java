package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import dev.kostromdan.mods.crash_assistant.app.utils.maven_version_cmp.VersionUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Utility class for detecting GPUs and their types using DirectX.
 */
public class DirectXGPUDetectorCommon {
    static {
        try {
            loadNativeLibraryFromJar();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library: " + e.getMessage(), e);
        }
    }

    private static void loadNativeLibraryFromJar() throws IOException {
        String libraryName = "gpu-detect-jni.dll";

        // Create a temporary directory to extract the DLL
        Path tempDir = Files.createTempDirectory("gpu-detect-jni");
        tempDir.toFile().deleteOnExit();

        // Extract the DLL to the temporary directory
        Path tempFile = tempDir.resolve(libraryName);
        try (InputStream in = DirectXGPUDetectorCommon.class.getClassLoader().getResourceAsStream(libraryName)) {
            if (in == null) {
                throw new IOException("Could not find " + libraryName + " in the JAR");
            }
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Load the DLL from the temporary location
        System.load(tempFile.toAbsolutePath().toString());
    }

    public static String getSerialisedGPUs() throws Exception {
        String result = "";
        if (VersionUtils.isGreaterThanOrEqual(PlatformHelp.javaVersion, "22.0.0")) {
            result += "Java version is >= 22. Using FFM.\n";
            // Use reflection to load the FFM implementation to avoid direct class reference
            // This allows the app to be compiled with Java 17 while the FFM module uses Java 22
            Class<?> ffmClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.utils.gpu.DirectXGPUDetectorFFM");
            java.lang.reflect.Method getSerialisedGPUsMethod = ffmClass.getMethod("getSerialisedGPUs");
            result += (String) getSerialisedGPUsMethod.invoke(null);
        } else {
            result += "Java version is <= 21. Using JNI.\n";
            result += DirectXGPUDetector.getSerialisedGPUs();
        }
        return result;
    }

    public static void main(String[] args) throws Exception { // Test
        String serialized = getSerialisedGPUs();
        System.out.println(serialized);
        List<GPU> gpus = GPU.deserializeGPUs(serialized);
        System.out.println(gpus);
    }
}
