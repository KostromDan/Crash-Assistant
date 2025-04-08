package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.loading_utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;


public class Create6Addons extends KnownCrashReason {
    public Create6Addons() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.c6a", new HashMap<>() {{
                    put("$LINK.C6A$", LanguageProvider.get("warnings_common.here"));
                }}),
                "Error loading class: com/simibubi/create/.* \\(java\\.lang\\.ClassNotFoundException: com\\.simibubi\\.create.*\\)",
                "Error loading class: com/jozufozu/flywheel/.* \\(java\\.lang\\.ClassNotFoundException: com\\.jozufozu\\.flywheel.*\\)",
                "Caused by: org\\.spongepowered\\.asm\\.mixin\\.throwables\\.ClassMetadataNotFoundException: net\\.createmod\\.catnip\\.data\\.Couple"
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) {
            return false;
        }
        if (getCurrentCreateMod().isEmpty()) {
            return false;
        }
//        if (!super.matches(log)) {
//            return false;
//        }

//        getInvalidCreateModDeps();

        return true;
    }

    public static Optional<Mod> getCurrentCreateMod() {
        return ModListUtils.getCurrentModList(true).stream().filter(mod -> Objects.equals(mod.getModId(), "create")).findFirst();
    }

    public static boolean isCreateClass(String className) {
        return (className.startsWith("com/simibubi/create") ||
                className.startsWith("com/jozufozu/flywheel") ||
                className.startsWith("net/createmod") ||
                className.startsWith("dev/engine_room")
        ) && className.endsWith(".class");
    }

    public static String fixClassName(String className) {
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }
        int dollarIndex = className.indexOf('$');
        if (dollarIndex != -1) {
            className = className.substring(0, dollarIndex);
        }
        return className;
    }

    public static String getJDepsPath() {
        String javaBinaryPath = JavaBinaryLocator.getJavaBinary(ProcessHandle.current());
        String jdepsPath = javaBinaryPath.replaceAll("(?<=[/\\\\])java(?:\\.exe)?$", "jdeps");
        if (Files.isRegularFile(Paths.get(javaBinaryPath)) && !Files.isRegularFile(Paths.get(jdepsPath))) {
            CrashAssistantApp.LOGGER.error("JDK is required for analysis of jar files. JRE is not suitable for this!");
            return null;
        }
        return jdepsPath;
    }

    public static HashSet<String> getCreateClassesModUsing(Mod mod) {
        HashSet<String> result = new HashSet<>();
        try {
            ProcessBuilder jdepsProcessBuilder = new ProcessBuilder(
                    getJDepsPath(),
                    "-verbose:class",
                    "mods/" + mod.getJarName()
            );
            jdepsProcessBuilder.redirectErrorStream(true);
            Process process = jdepsProcessBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    int arrowIndex = line.indexOf("->");
                    if (arrowIndex != -1) {
                        String dependency = line.substring(arrowIndex + 2).trim();
                        if (dependency.endsWith(".class")) continue;
                        int spaceIndex = dependency.indexOf(' ');
                        String classPath = dependency.substring(0, spaceIndex == -1 ? dependency.length() : spaceIndex).replace('.', '/');
                        classPath += ".class";
                        if (isCreateClass(classPath)) {
                            result.add(fixClassName(classPath));
                        }
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing create mod deps: ", e);
        }

        return result;
    }

    public static HashSet<String> getCurrentCreateClasses(Mod createMod) {
        HashSet<String> currentCreateClasses = new HashSet<>();

        try {
            try (JarFile jarFile = new JarFile(Paths.get("mods", createMod.getJarName()).toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (isCreateClass(name)) {
                        currentCreateClasses.add(fixClassName(name));
                        continue;
                    }

                    if (name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) {
                        InputStream nestedJarStream = jarFile.getInputStream(entry);

                        try (JarInputStream nestedJar = new JarInputStream(nestedJarStream)) {
                            JarEntry nestedEntry;
                            while ((nestedEntry = nestedJar.getNextJarEntry()) != null) {
                                String className = nestedEntry.getName();
                                if (isCreateClass(className)) {
                                    currentCreateClasses.add(fixClassName(className));
                                }
                            }
                        }
                    }
                }
            }
            CrashAssistantApp.LOGGER.info("Found " + currentCreateClasses.size() + " create classes in " + createMod.getJarName());

        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing create mod deps: ", e);
        }
        return currentCreateClasses;
    }

    public static Map<Mod, Set<String>> getInvalidCreateModDeps() {
        Map<Mod, Set<String>> invalidCreateDeps = new ConcurrentHashMap<>();

        Mod createMod = getCurrentCreateMod().orElse(null);
        if (createMod == null) {
            return invalidCreateDeps;
        }

        Set<String> currentCreateClasses = getCurrentCreateClasses(createMod);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (final Mod mod : ModListUtils.getCurrentModList(true)) {
            if (Objects.equals(mod.getModId(), "create")) continue;
            executor.submit(() -> {
                HashSet<String> deps = getCreateClassesModUsing(mod);
                for (String dep : deps) {
                    if (!currentCreateClasses.contains(dep)) {
                        invalidCreateDeps.computeIfAbsent(mod, k -> new HashSet<>()).add(dep);
                    }
                }

                if (invalidCreateDeps.containsKey(mod)) {
                    CrashAssistantApp.LOGGER.warn(
                            "Found {} Create mod class dependency(ies) in {}, which are missing from the current {}",
                            invalidCreateDeps.get(mod).size(), mod.getJarName(), createMod.getJarName());
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        return invalidCreateDeps;
    }
}
