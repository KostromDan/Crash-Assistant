package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

public class ModuleFinder {
    public static synchronized List<String> findJarsInFolderAsync(List<String> packagePrefixes, LinkedHashSet<Mod> mods) {
        Path modsFolderPath = Paths.get("mods");
        ExecutorService executor = Executors.newWorkStealingPool();

        Map<String, Path> jarMap = new HashMap<>();
        try {
            Files.walk(modsFolderPath)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .forEach(jarPath -> jarMap.put(jarPath.getFileName().toString(), jarPath));
        } catch (IOException e) {
            executor.shutdown();
            return new ArrayList<>();
        }

        List<CompletableFuture<List<String>>> tasks = new ArrayList<>();
        for (Mod mod : mods) {
            CompletableFuture<List<String>> task = CompletableFuture.supplyAsync(() -> {
                Path jarPath = jarMap.get(mod.getJarName());
                if (jarPath != null) {
                    return findJarsContainingEntries(packagePrefixes, jarPath);
                }
                return new ArrayList<>();
            }, executor);
            tasks.add(task);
        }

        List<String> allResults = new ArrayList<>();
        for (CompletableFuture<List<String>> task : tasks) {
            try {
                allResults.addAll(task.get());
            } catch (Exception e) {
            }
        }

        executor.shutdown();
        return allResults;
    }

    public static List<String> findJarsContainingEntries(List<String> packagePrefixes, Path jarPath) {
        List<String> pathPrefixes = packagePrefixes.stream()
                .map(ModuleFinder::normalizeModuleName)
                .collect(Collectors.toList());
        List<String> results = new ArrayList<>();
        String topName = jarPath.getFileName().toString();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            boolean matchedTop = false;
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!matchedTop) {
                    for (String prefix : pathPrefixes) {
                        if (normalizeModuleName(name).startsWith(prefix)) {
                            results.add(topName);
                            matchedTop = true;
                            break;
                        }
                    }
                }
                if (!entry.isDirectory() && name.endsWith(".jar")) {
                    processNestedJar(name,
                            () -> readAllBytes(jarFile.getInputStream(entry)),
                            pathPrefixes,
                            topName + "!/" + name,
                            results);
                }
            }
        } catch (IOException e) {
        }
        return results;
    }

    private static void processNestedJar(String entryName, ByteSupplier supplier, List<String> pathPrefixes, String containerName, List<String> results) {
        try {
            byte[] data = supplier.get();
            try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(data))) {
                boolean matched = false;
                JarEntry ne;
                while ((ne = jis.getNextJarEntry()) != null) {
                    String n = ne.getName();
                    if (!matched) {
                        for (String prefix : pathPrefixes) {
                            if (normalizeModuleName(n).startsWith(prefix)) {
                                results.add(containerName);
                                matched = true;
                                break;
                            }
                        }
                    }
                    if (!ne.isDirectory() && n.endsWith(".jar")) {
                        processNestedJar(n,
                                () -> readAllBytes(jis),
                                pathPrefixes,
                                containerName + "!/" + n,
                                results);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int r;
        while ((r = in.read(tmp)) != -1) buf.write(tmp, 0, r);
        return buf.toByteArray();
    }

    @FunctionalInterface
    private interface ByteSupplier {
        byte[] get() throws Exception;
    }

    public static String normalizeModuleName(String moduleName) {
        moduleName = moduleName.toLowerCase().replace('.', '/');
        if (!moduleName.endsWith("/")) {
            moduleName += "/";
        }
        return moduleName;
    }
}
