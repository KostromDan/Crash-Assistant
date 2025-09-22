package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for detecting GPUs and their types using DirectX.
 */
public class DirectXGPUDetector {
    public static String getSerialisedGPUs() {
        File csharpFile = null;
        try {
            // 1. Load the C# code from the classpath resource
            StringBuilder csharpCodeBuilder = new StringBuilder();
            try (InputStream is = DirectXGPUDetector.class.getResourceAsStream("/gpu_detect.cs")) {
                if (is == null) {
                    throw new IOException("Resource not found: /gpu_detect.cs. DirectX gpu detection won't work! Maybe Crash Assistant mod jar is corrupted?");
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        csharpCodeBuilder.append(line).append(System.lineSeparator());
                    }
                }
            }
            String csharpCode = csharpCodeBuilder.toString();

            // 2. Create a temporary file in the specified relative directory
            File directory = new File(Paths.get("local", "crash_assistant").toUri());
            if (!directory.exists()) {
                directory.mkdirs();
            }
            String fileName = Objects.toString(Boot.parentPID) + "_" + Boot.parentStarted + "_gpu_detect.cs";
            csharpFile = new File(directory, fileName);

            // 3. Write the loaded C# code to the temporary file
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(csharpFile), StandardCharsets.UTF_8)) {
                writer.write(csharpCode);
            }

            // 4. Construct the PowerShell command to read from the file
            String psCommand = String.format(
                    "$csharpCode = Get-Content -Path '%s' -Raw; Add-Type -TypeDefinition $csharpCode; [GpuDetector]::DetectGpus() | ForEach-Object { Write-Output $_ }",
                    csharpFile.getAbsolutePath()
            );

            // 5. Execute the command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-Command", psCommand
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 6. Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            process.waitFor();
            return output.toString();

        } catch (Exception e) {
            return "Error executing PowerShell command: " + e.getMessage();
        } finally {
            // 7. Clean up the temporary C# file
            if (csharpFile != null && csharpFile.exists()) {
                csharpFile.delete();
            }
        }
    }

    public static void main(String[] args) throws Exception { // Test
        String serialized = getSerialisedGPUs();
        System.out.println(serialized);
        List<GPU> gpus = GPU.deserializeGPUs(serialized);
        System.out.println(gpus);
    }
}