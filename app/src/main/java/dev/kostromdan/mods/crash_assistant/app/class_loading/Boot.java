package dev.kostromdan.mods.crash_assistant.app.class_loading;

import dev.kostromdan.mods.crash_assistant.app.gui.LinuxDesktopIntegration;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.permissions.Permissions;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ErrorUtils;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import org.apache.commons.jexl3.annotations.NoJexl;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class Boot {
    private static String classPath = null;
    private static boolean recursiveStart = false;
    private static boolean gpuDetect = false;
    private static boolean bootWarningsVisible = false;
    private static String bootWarningsJson = null;
    private static String startupWarningsJson = null;
    private static String serialisedGPUs = null;
    public static String crashAssistantModJarName = null;
    public static boolean vulkanAddonLoaded = false;
    public static long parentPID = -1;
    public static long parentStarted = -1;
    public static List<String> JVM_ARGS = ManagementFactory.getRuntimeMXBean().getInputArguments();
    public static List<String> APP_ARGS;
    public static String MINECRAFT_LAUNCH_COMMAND;
    public static String MINECRAFT_JVM_ARGS;
    public static String MINECRAFT_CLASS_PATH;

    static {
        if (Permissions.isDevEnvironment()) {
            MINECRAFT_LAUNCH_COMMAND = System.getProperty("minecraft.launch.arguments");
        }
    }


    @NoJexl
    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            List<String> effectiveArgs = new ArrayList<String>();
            String argsFilePath = null;

            for (int i = 0; i < args.length; i++) {
                if ("--args-file".equals(args[i]) && i + 1 < args.length) {
                    argsFilePath = args[i + 1];
                    Path argsFile = Paths.get(argsFilePath);
                    if (Files.exists(argsFile)) {
                        effectiveArgs.addAll(Files.readAllLines(argsFile, StandardCharsets.UTF_8));
                    }
                } else if ("-recursiveStart".equals(args[i])) {
                    recursiveStart = true;
                } else if ("-gpuDetect".equals(args[i])) {
                    gpuDetect = true;
                } else if ("-bootWarningsVisible".equals(args[i])) {
                    bootWarningsVisible = true;
                }
            }

            APP_ARGS = effectiveArgs;

            for (int i = 0; i < effectiveArgs.size(); i++) {
                if ("-parentPID".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    parentPID = Long.parseLong(effectiveArgs.get(i + 1));
                } else if ("-parentStarted".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    parentStarted = Long.parseLong(effectiveArgs.get(i + 1));
                } else if ("-crashAssistantModJarName".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    crashAssistantModJarName = effectiveArgs.get(i + 1);
                } else if ("-classPath".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    classPath = effectiveArgs.get(i + 1);
                } else if ("-serialisedGPUs".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    serialisedGPUs = new String(Base64.getDecoder().decode(effectiveArgs.get(i + 1)), StandardCharsets.UTF_8);
                } else if ("-minecraftStartCommand".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    MINECRAFT_LAUNCH_COMMAND = new String(Base64.getDecoder().decode(effectiveArgs.get(i + 1)), StandardCharsets.UTF_8);
                } else if ("-minecraftJvmArgs".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    MINECRAFT_JVM_ARGS = new String(Base64.getDecoder().decode(effectiveArgs.get(i + 1)), StandardCharsets.UTF_8);
                } else if ("-minecraftClassPath".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    MINECRAFT_CLASS_PATH = new String(Base64.getDecoder().decode(effectiveArgs.get(i + 1)), StandardCharsets.UTF_8);
                }else if ("-modLoadedWithConnector".equals(effectiveArgs.get(i))){
                    PlatformHelp.modLoadedWithConnector = true;
                } else if ("-startupWarnings".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    startupWarningsJson = new String(Base64.getDecoder().decode(effectiveArgs.get(i + 1)), StandardCharsets.UTF_8);
                } else if ("-bootWarnings".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                    bootWarningsJson = new String(Base64.getDecoder().decode(effectiveArgs.get(i + 1)), StandardCharsets.UTF_8);
                }
            }

            List<String> missingParameters = getMissingParameters();
            if (!missingParameters.isEmpty()) {
                System.err.println("Missing required parameters: " + String.join(", ", missingParameters) +
                        "\nIf you trying to run app from dev env, run CrashAssistantApp.");
                System.exit(-1);
            }

            if (gpuDetect) {
                loadVulkanAddon();
                try {
                    Class<?> GPUDetectorClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.utils.gpu.GPUDetector");
                    Method getSerialisedGPUsMethod = GPUDetectorClass.getMethod("getSerialisedGPUs");
                    serialisedGPUs = (String) getSerialisedGPUsMethod.invoke(null);
                } catch (Throwable e) {
                    serialisedGPUs = ErrorUtils.getErrorMessageAndStackTrace(e);
                }
                System.out.println(serialisedGPUs);
                System.exit(0);
            }

            if (bootWarningsVisible) {
                try {
                    Class<?> startupWarningViewerClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.StartupWarningViewer");
                    Method mainMethod = startupWarningViewerClass.getMethod("main", String[].class);
                    
                    String encodedWarnings = null;
                     for (int i = 0; i < effectiveArgs.size(); i++) {
                        if ("-bootWarnings".equals(effectiveArgs.get(i)) && i + 1 < effectiveArgs.size()) {
                            encodedWarnings = effectiveArgs.get(i+1);
                            break;
                        }
                    }
                    if (encodedWarnings != null) {
                        mainMethod.invoke(null, (Object) new String[]{encodedWarnings});
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                System.exit(0);
            }

            /**
             * If Minecraft JVM terminated by windows itself, all child processes will be also terminated.
             * So Crash Assistant can't be child process. This way we make Crash Assistant completely independent process.
             *
             * Also, here we're locating GPUs with Vulkan or DirectX, it's increasing heap before GUI start,
             * so we're doing it on this TMP process, to not waste user resources on App awaiting stage.
             */
            if (!recursiveStart) {
                List<String> baseChildCommand = new ArrayList<>();
                baseChildCommand.add(JavaBinaryLocator.getJavaBinary());
                baseChildCommand.addAll(JVM_ARGS);
                baseChildCommand.add("-cp");
                baseChildCommand.add(classPath);
                baseChildCommand.add("dev.kostromdan.mods.crash_assistant.app.class_loading.Boot");
                baseChildCommand.add("--args-file");
                baseChildCommand.add(argsFilePath);

                serialisedGPUs = getSerializedGPUsOnAnotherProcess(new ArrayList<>(baseChildCommand));
                if (serialisedGPUs != null) {
                    String encodedGPUs = Base64.getEncoder().encodeToString(serialisedGPUs.getBytes(StandardCharsets.UTF_8));
                    Files.write(Paths.get(argsFilePath), Arrays.asList("-serialisedGPUs", encodedGPUs), StandardOpenOption.APPEND);
                }

                if (bootWarningsJson != null) {
                     String warningsOutput = getBootWarningsOutput(new ArrayList<>(baseChildCommand));
                     if (warningsOutput != null) {
                         String encodedWarnsOutput = Base64.getEncoder().encodeToString(warningsOutput.getBytes(StandardCharsets.UTF_8));
                         Files.write(Paths.get(argsFilePath), Arrays.asList("-warnsProcessOutput", encodedWarnsOutput), StandardOpenOption.APPEND);
                     }
                }

                List<String> finalLaunchCommand = new ArrayList<>(baseChildCommand);
                finalLaunchCommand.add("-recursiveStart");

                ProcessBuilder pb = new ProcessBuilder(finalLaunchCommand);
                pb.start();
                System.exit(0);
            }

            LinuxDesktopIntegration.prepare();
            Class<?> crashAssistantAppClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp");
            Method mainMethod = crashAssistantAppClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) effectiveArgs.toArray(new String[0]));
        } catch (Throwable e) {
            Path logsFolder = Paths.get("logs", "crash_assistant");
            Files.createDirectories(logsFolder);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String errorDetails = "CrashAssistantApp process failed to start due to errors below:\n" +
                    "Crash Assistant won't work.\n" +
                    "This won't cause any issues to the main game process, just Crash Assistant won't popup after crash.\n" +
                    "Please report to https://github.com/KostromDan/Crash-Assistant/issues\n"
                    + sw.toString();
            Path errorFile = logsFolder.resolve("app_start_error.txt");
            try {
                Files.write(
                        errorFile,
                        (errorDetails + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE
                );
            } catch (IOException ioe) {
                System.err.println("Failed to write crash log: " + ioe.getMessage());
            }
        }
    }

    private static List<String> getMissingParameters() {
        List<String> missingParameters = new ArrayList<>();
        if (crashAssistantModJarName == null) missingParameters.add("-crashAssistantModJarName");
        if (classPath == null) missingParameters.add("-classPath");
        return missingParameters;
    }

    private static void loadVulkanAddon() {
//        List<Mod> vulkanAddons = JarInJarHelper.getModsContainingPart("CrashAssistantVulkanGPUDetectionAddon-");
//        Mod VulkanAddon = vulkanAddons.stream()
//                .findFirst()
//                .orElse(null);
//        if (VulkanAddon == null) return;
//
//        vulkanAddonLoaded = true;
//
//        try {
//            Path libsFolder = Paths.get("local", "crash_assistant", "libs");
//            Files.createDirectories(libsFolder);
//
//            try (JarFile vulkanAddonJar = new JarFile(Paths.get("mods", VulkanAddon.getJarName()).toFile())) {
//                Enumeration<JarEntry> entries = vulkanAddonJar.entries();
//                while (entries.hasMoreElements()) {
//                    JarEntry entry = entries.nextElement();
//                    String entryName = entry.getName();
//
//                    if (entryName.startsWith("META-INF/jarjar/") && entryName.endsWith(".jar")) {
//                        String jarFileName = entryName.substring(entryName.lastIndexOf('/') + 1);
//                        Path extractedJarPath = libsFolder.resolve(jarFileName);
//
//                        try (InputStream is = vulkanAddonJar.getInputStream(entry)) {
//                            Files.copy(is, extractedJarPath, StandardCopyOption.REPLACE_EXISTING);
//                            CrashAssistantAgent.appendJarFile(extractedJarPath.toAbsolutePath().toString());
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Failed to extract jar from VulkanAddon: " + e.getMessage());
//            e.printStackTrace();
//        }
    }

    private static String getSerializedGPUsOnAnotherProcess(List<String> argsList) {
        try {
            argsList.add("-gpuDetect");
            ProcessBuilder pb = new ProcessBuilder(argsList);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(10000, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("GPUDetector process reached timeout of 10 seconds and was killed.");
            }

            int exitCode = process.exitValue();

            try (InputStream is = process.getInputStream()) {
                StringBuilder output = new StringBuilder();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                }

                String result = output.toString();
                if (exitCode == 0) return result;
                return "GPUDetector process exited with non zero exit code: " + exitCode + "\n" +
                        "STDOUT:\n" +
                        result;
            }

        } catch (Throwable ignored) {
            return "Error while getting gpus with GPUDetector process: " + ErrorUtils.getErrorMessageAndStackTrace(ignored);
        }
    }

    private static String getBootWarningsOutput(List<String> argsList) {
        try {
            argsList.removeIf(arg -> arg.startsWith("-Dlog4j2.configurationFile="));
            argsList.add(1, "-Dlog4j2.configurationFile=log4j2-console.xml");
            argsList.add("-bootWarningsVisible");
            ProcessBuilder pb = new ProcessBuilder(argsList);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            process.waitFor();

            try (InputStream is = process.getInputStream()) {
                StringBuilder output = new StringBuilder();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                }
                return output.toString();
            }

        } catch (Throwable ignored) {
            return "Error while getting output from boot warnings process: " + ErrorUtils.getErrorMessageAndStackTrace(ignored);
        }
    }

    public static String getStartupWarningsJson() {
        return startupWarningsJson;
    }

    public static String getSerialisedGPUs(){
        return serialisedGPUs;
    }
}
