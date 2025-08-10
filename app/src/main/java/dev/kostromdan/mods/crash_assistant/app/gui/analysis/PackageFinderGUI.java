package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PackageFinderGUI extends AnalysisGUIBase {
    private final String searchTerm;
    private final String originalSearchTerm;

    public PackageFinderGUI(JFrame parent, String search) {
        super(parent, "Package/Class Finder", "This tool scans mods to find which ones contain the specified package or class.<br>This can help identify which mod provides a specific resource.");
        this.originalSearchTerm = search.trim();
        String term = originalSearchTerm;
        // If it's a class name with an extension, remove it for a broader search.
        if (term.toLowerCase().endsWith(".class")) {
            term = term.substring(0, term.length() - 6);
        }
        this.searchTerm = term.replace('.', '/');
    }

    public static void showPackageFinderDialog(JFrame parent) {
        String lastSearch = (String) CrashAssistantLocalConfig.get("analysis.package_finder_last_search");

        String input = (String) JOptionPane.showInputDialog(
                parent,
                "Enter a package (e.g.`org.mozilla.javascript`) or class name (e.g. `IMPConfig`) to search for inside mods (case-insensitive):",
                "Package/Class Finder",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                lastSearch
        );

        if (input != null && !input.trim().isEmpty()) {
            CrashAssistantLocalConfig.set("analysis.package_finder_last_search", input.trim());
            new PackageFinderGUI(parent, input.trim()).start();
        }
    }

    @Override
    protected void performAnalysis() {
        LinkedHashSet<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true);
        int totalMods = modsToAnalyze.size();
        AtomicInteger completedTasks = new AtomicInteger(0);
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        Map<String, List<String>> foundInMods = new TreeMap<>(); // Map<RootJar, List<FoundPath>>

        for (Mod mod : modsToAnalyze) {
            executor.submit(() -> {
                if (isCancelled) return;

                SwingUtilities.invokeLater(() -> currentJarLabel.setText("Current mod: " + mod.getJarName()));

                List<String> foundPaths;
                try {
                    Path jarPath = Paths.get("mods", mod.getJarName());
                    // Use the flexible search mode for classes or packages
                    foundPaths = ModuleFinder.findJarsContainingEntries(Collections.singletonList(searchTerm), jarPath, ModuleFinder.SearchMode.CLASS_OR_PACKAGE);
                } catch (Exception e) {
                    CrashAssistantApp.LOGGER.error("Error scanning mod " + mod.getJarName(), e);
                    foundPaths = Collections.emptyList();
                }

                if (!foundPaths.isEmpty()) {
                    synchronized (foundInMods) {
                        foundInMods.put(mod.getJarName(), foundPaths);
                    }
                }

                int completed = completedTasks.incrementAndGet();
                SwingUtilities.invokeLater(() -> {
                    if (!isCancelled) {
                        progressBar.setValue(completed);
                    }
                });
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!isCancelled) {
            SwingUtilities.invokeLater(() -> {
                List<String> allPaths = new ArrayList<>();
                foundInMods.values().forEach(allPaths::addAll);

                if (allPaths.isEmpty()) {
                    appendStyledText("'" + originalSearchTerm + "' not found in any mod.\n", NORMAL_COLOR);
                } else {
                    appendStyledText("Found '" + originalSearchTerm + "' in " + allPaths.stream().distinct().count() + " location(s):\n\n", NORMAL_COLOR);
                    allPaths.stream().distinct().sorted().forEach(path -> {
                        appendStyledText(path + "\n", MOD_COLOR);
                    });
                }
            });
        }
    }
}