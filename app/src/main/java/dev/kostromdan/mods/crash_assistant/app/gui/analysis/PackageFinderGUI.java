package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PackageFinderGUI extends AnalysisGUIBase {
    private final String searchTerm;
    private final String originalSearchTerm;

    public PackageFinderGUI(JFrame parent, String search) {
        super(parent, LanguageProvider.get("gui.menu.analysis.package_class_finder"), LanguageProvider.get("gui.analysis.package_finder.header"));
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
                LanguageProvider.get("gui.analysis.package_finder.input_message"),
                LanguageProvider.get("gui.menu.analysis.package_class_finder"),
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
        AtomicInteger foundCounter = new AtomicInteger(0);
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        for (Mod mod : modsToAnalyze) {
            executor.submit(() -> {
                if (isCancelled) return;

                SwingUtilities.invokeLater(() -> currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + mod.getJarName()));

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
                    if (foundCounter.getAndIncrement() == 0) {
                        String msg = LanguageProvider.get("gui.analysis.package_finder.found")
                                .replace("$TERM$", originalSearchTerm);
                        SwingUtilities.invokeLater(() -> appendStyledText(msg, NORMAL_COLOR));
                    }
                    List<String> finalFoundPaths = foundPaths;
                    SwingUtilities.invokeLater(() -> {
                        for (String path : finalFoundPaths) {
                            appendStyledText(path + "\n", MOD_COLOR);
                        }
                    });
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

        if (!isCancelled && foundCounter.get() == 0) {
            SwingUtilities.invokeLater(() -> {
                String msg = LanguageProvider.get("gui.analysis.package_finder.not_found")
                        .replace("$TERM$", originalSearchTerm);
                appendStyledText(msg, NORMAL_COLOR);
            });
        }
    }
}