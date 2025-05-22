package dev.kostromdan.mods.crash_assistant.app.class_loading;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ErrorUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Boot {
    public static String log4jApi = null;
    public static String log4jCore = null;
    public static String googleGson = null;
    public static String commonIo = null;
    public static String lwjglNatives = null;
    public static String processor = null;
    public static String jarPath = null;
    public static String crashAssistantModJarPath = null;
    public static boolean recursiveStart = false;
    public static String serialisedGPUs = null;
    public static List<String> JVM_ARGS = ManagementFactory.getRuntimeMXBean().getInputArguments();
    public static List<String> APP_ARGS;


    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        APP_ARGS = List.of(args);
        for (int i = 0; i < args.length; i++) {
            if ("-log4jApi".equals(args[i]) && i + 1 < args.length) {
                log4jApi = args[i + 1];
            } else if ("-log4jCore".equals(args[i]) && i + 1 < args.length) {
                log4jCore = args[i + 1];
            } else if ("-googleGson".equals(args[i]) && i + 1 < args.length) {
                googleGson = args[i + 1];
            } else if ("-commonIo".equals(args[i]) && i + 1 < args.length) {
                commonIo = args[i + 1];
            } else if ("-lwjglNatives".equals(args[i]) && i + 1 < args.length) {
                lwjglNatives = args[i + 1];
            } else if ("-processor".equals(args[i]) && i + 1 < args.length) {
                processor = args[i + 1];
            } else if ("-jarPath".equals(args[i]) && i + 1 < args.length) {
                jarPath = args[i + 1];
            } else if ("-crashAssistantModJarPath".equals(args[i]) && i + 1 < args.length) {
                crashAssistantModJarPath = args[i + 1];
            } else if ("-serialisedGPUs".equals(args[i]) && i + 1 < args.length) {
                serialisedGPUs = new String(Base64.getDecoder().decode(args[i + 1]), StandardCharsets.UTF_8);
            } else if ("-recursiveStart".equals(args[i])) {
                recursiveStart = true;
            }
        }

        List<String> missingParameters = getMissingParameters();
        if (!missingParameters.isEmpty()) {
            System.err.println("Missing required parameters: " + String.join(", ", missingParameters) +
                    "\nIf you trying to run app from dev env, run CrashAssistantApp.");
            System.exit(-1);
        }

        CrashAssistantAgent.appendJarFile(log4jApi);
        CrashAssistantAgent.appendJarFile(log4jCore);
        CrashAssistantAgent.appendJarFile(googleGson);
        CrashAssistantAgent.appendJarFile(commonIo);
        CrashAssistantAgent.appendJarFile(crashAssistantModJarPath);
        ifBlock:
        if (lwjglNatives != null && !Objects.equals(lwjglNatives, "UNDEFINED") && !recursiveStart) {
            CrashAssistantAgent.appendJarFile(lwjglNatives);

            final String REQUIRED_VULKAN_ADDON_VERSION = "3.3.1";
            List<Mod> vulkanAddons = JarInJarHelper.getModsContainingPart("CrashAssistantVulkanGPUDetectionAddon-");
            Mod VulkanAddon = vulkanAddons.stream()
                    .filter(mod -> REQUIRED_VULKAN_ADDON_VERSION.equals(mod.getVersion()))
                    .findFirst()
                    .orElse(null);
            if(VulkanAddon == null) break ifBlock;
            
            try {
                Path libsFolder = Paths.get("local", "crash_assistant", "libs");
                Files.createDirectories(libsFolder);
                
                try (JarFile vulkanAddonJar = new JarFile(Paths.get("mods", VulkanAddon.getJarName()).toFile())) {
                    Enumeration<JarEntry> entries = vulkanAddonJar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();
                        
                        if (entryName.startsWith("META-INF/jarjar/") && entryName.endsWith(".jar")) {
                            String jarFileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                            Path extractedJarPath = libsFolder.resolve(jarFileName);
                            
                            try (InputStream is = vulkanAddonJar.getInputStream(entry)) {
                                Files.copy(is, extractedJarPath, StandardCopyOption.REPLACE_EXISTING);
                                CrashAssistantAgent.appendJarFile(extractedJarPath.toAbsolutePath().toString());
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to extract jar from VulkanAddon: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            lwjglNatives = null;
        }

        /**
         * If Minecraft JVM terminated by windows itself, all child processes will be also terminated.
         * So Crash Assistant can't be child process. This way we make Crash Assistant completely independent process.
         *
         * Also, here we're locating GPUs with Vulkan or DirectX, it's increasing heap before GUI start,
         * so we're doing it on this TMP process, to not waste user resources on App avaiting stage.
         */
        if (!recursiveStart) {
            try {
                Class<?> GPUDetectorClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.utils.gpu.GPUDetector");
                Method getSerialisedGPUsMethod = GPUDetectorClass.getMethod("getSerialisedGPUs");
                serialisedGPUs = (String) getSerialisedGPUsMethod.invoke(null);
            } catch (Exception e) {
                serialisedGPUs = ErrorUtils.getErrorMessageAndStackTrace(e);
            }

            List<String> argsList = new ArrayList<>();
            argsList.add(JavaBinaryLocator.getJavaBinary(ProcessHandle.current()));
            argsList.addAll(JVM_ARGS);
            argsList.add("-jar");
            argsList.add(jarPath);
            argsList.addAll(APP_ARGS);
            if (serialisedGPUs != null) {
                String encodedGPUs = Base64.getEncoder().encodeToString(serialisedGPUs.getBytes(StandardCharsets.UTF_8));
                argsList.add("-serialisedGPUs");
                argsList.add(encodedGPUs);
            }
            argsList.add("-recursiveStart");
            ProcessBuilder pb = new ProcessBuilder(argsList);
            pb.start();
            System.exit(0);
        }

        Class<?> crashAssistantAppClass = Class.forName("dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp");
        Method mainMethod = crashAssistantAppClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }

    private static List<String> getMissingParameters() {
        List<String> missingParameters = new ArrayList<>();
        if (log4jApi == null) missingParameters.add("-log4jApi");
        if (log4jCore == null) missingParameters.add("-log4jCore");
        if (googleGson == null) missingParameters.add("-googleGson");
        if (commonIo == null) missingParameters.add("-commonIo");
        if (processor == null) missingParameters.add("-processor");
        if (jarPath == null) missingParameters.add("-jarPath");
        if (crashAssistantModJarPath == null) missingParameters.add("-crashAssistantModJarPath");
        return missingParameters;
    }
}
