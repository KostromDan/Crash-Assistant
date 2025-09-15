package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
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
        JarEntriesScanner.scanJar(jarPath, true, (containerName, entries) -> {
            boolean matched = false;
            for (Map.Entry<String, Boolean> e : entries.entrySet()) {
                String name = e.getKey();
                boolean isDir = e.getValue();
                if (!matched) {
                    for (String prefix : searchPrefixes) {
                        if (matches(name, isDir, prefix, mode)) {
                            results.add(containerName);
                            matched = true;
                            break;
                        }
                    }
                }
                if (name.equals("module-info.class")) {
                    JarInJarHelper.LOGGER.warn("Found module-info.class in " + containerName);
                }
            }
        });
        return results;
    }

    public static List<String> findJarsInFolderAsync(List<String> packagePrefixes, LinkedHashSet<Mod> mods) {
        return findJarsInFolderAsync(packagePrefixes, mods, SearchMode.PACKAGE);
    }


    public static List<String> findJarsInFolderAsync(List<String> packagePrefixes, LinkedHashSet<Mod> mods, SearchMode mode) {
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
                    return findJarsContainingEntries(packagePrefixes, jarPath, mode);
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


    private static boolean matches(JarEntry entry, String searchTerm, SearchMode mode) {
        return matches(entry.getName(), entry.isDirectory(), searchTerm, mode);
    }

    private static boolean matches(String entryName, boolean isDirectory, String searchTerm, SearchMode mode) {
        String normalizedEntryName = normalizeModuleName(entryName);
        boolean isClass = entryName.endsWith(".class");
        boolean isPackage = isDirectory;
        if (!isClass && !isPackage) return false;
        if (isPackage) {
            return normalizedEntryName.equals(searchTerm);
        }
        if (mode == SearchMode.PACKAGE) {
            return false;
        }
        String withoutExt = entryName.substring(0, entryName.length() - 6);
        String normalizedWithout = normalizedEntryName.substring(0, normalizedEntryName.length() - 6);
        if (normalizedWithout.equals(searchTerm)) {
            return true;
        }
        String className = withoutExt.substring(withoutExt.lastIndexOf('/') + 1).toLowerCase();
        if ((className + "/").equals(searchTerm)) return true;

        AtomicBoolean found = new AtomicBoolean(false);
        Arrays.stream(className.split("\\$")).map(s -> s + "/").forEach(name -> {
            if (name.equals(searchTerm)) {
                found.set(true);
            }
        });
        return found.get();
    }


    public static String normalizeModuleName(String moduleName) {
        moduleName = moduleName.toLowerCase().replace('.', '/');
        if (!moduleName.endsWith("/")) {
            moduleName += "/";
        }
        return moduleName;
    }
}