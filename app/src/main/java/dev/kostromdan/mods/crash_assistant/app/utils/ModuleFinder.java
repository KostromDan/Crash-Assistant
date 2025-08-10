package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleFinder {
    public enum SearchMode {
        PACKAGE,
        CLASS_OR_PACKAGE
    }

    public static List<String> findJarsContainingEntries(List<String> packagePrefixes, Path jarPath) {
        return findJarsContainingEntries(packagePrefixes, jarPath, SearchMode.PACKAGE);
    }

    public static List<String> findJarsContainingEntries(List<String> searchTerms, Path jarPath, SearchMode mode) {
        List<String> searchPrefixes = searchTerms.stream()
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
                    for (String prefix : searchPrefixes) {
                        if (matches(entry, prefix, mode)) {
                            results.add(topName);
                            matchedTop = true;
                            break;
                        }
                    }
                }
                if (name.equals("module-info.class")) {
                    JarInJarHelper.LOGGER.warn("Found module-info.class in " + topName);
                }
                if (!entry.isDirectory() && name.endsWith(".jar")) {
                    processNestedJar(
                            () -> readAllBytes(jarFile.getInputStream(entry)),
                            searchPrefixes,
                            topName + "!/" + name,
                            results,
                            mode);
                }
            }
        } catch (IOException e) {
            // Suppress errors
        }
        return results;
    }


    public static List<String> findJarsInFolderAsync(List<String> packagePrefixes, LinkedHashSet<Mod> mods) {
        Path modsFolderPath = Paths.get("mods");
        ExecutorService executor = Executors.newWorkStealingPool();

        Map<String, Path> jarMap = new HashMap<>();
        try (Stream<Path> stream = Files.walk(modsFolderPath)) {
            stream.filter(path -> path.toString().endsWith(".jar"))
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
            } catch (Exception ignored) {
            }
        }

        executor.shutdown();
        return allResults;
    }

    private static void processNestedJar(ByteSupplier supplier, List<String> searchPrefixes, String containerName, List<String> results, SearchMode mode) {
        try {
            byte[] data = supplier.get();
            try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(data))) {
                boolean matched = false;
                JarEntry ne;
                while ((ne = jis.getNextJarEntry()) != null) {
                    if (!matched) {
                        for (String prefix : searchPrefixes) {
                            if (matches(ne, prefix, mode)) {
                                results.add(containerName);
                                matched = true;
                                break;
                            }
                        }
                    }
                    if (ne.getName().equals("module-info.class")) {
                        JarInJarHelper.LOGGER.warn("Found module-info.class in " + containerName);
                    }
                    if (!ne.isDirectory() && ne.getName().endsWith(".jar")) {
                        processNestedJar(
                                () -> readAllBytes(jis),
                                searchPrefixes,
                                containerName + "!/" + ne.getName(),
                                results,
                                mode);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean matches(JarEntry entry, String searchTerm, SearchMode mode) {
        String entryName = entry.getName();
        String normalizedEntryName = normalizeModuleName(entryName);
        boolean isClass = entryName.endsWith(".class");
        boolean isPackage = entry.isDirectory();
        if (!isClass && !isPackage) return false;
        if (isPackage) {
            return normalizedEntryName.equals(searchTerm);
        }
        if (mode == SearchMode.PACKAGE) {
            return false;
        }
        entryName = entryName.substring(0, entryName.length() - 6);
        normalizedEntryName = normalizedEntryName.substring(0, normalizedEntryName.length() - 6);
        if (normalizedEntryName.equals(searchTerm)) {
            return true;
        }
        String className = entryName.substring(entryName.lastIndexOf('/') + 1).toLowerCase();
        if ((className + "/").equals(searchTerm)) return true;

        AtomicBoolean found = new AtomicBoolean(false);
        Arrays.stream(className.split("\\$")).map(s -> s + "/").forEach(name -> {
            if (name.equals(searchTerm)) {
                found.set(true);
            }
        });
        return found.get();
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
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