package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectorModDetectorGUI extends AnalysisGUIBase {

    public ConnectorModDetectorGUI(JFrame parent) {
        super(parent, LanguageProvider.get("gui.menu.analysis.connector_mod_detector"), LanguageProvider.get("gui.analysis.connector_detector.header"));
    }

    public static void showConnectorModDetectorDialog(JFrame parent) {
        new ConnectorModDetectorGUI(parent).start();
    }

    @Override
    protected void performAnalysis() {
        LinkedHashSet<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true);
        int totalMods = modsToAnalyze.size();
        AtomicInteger completedTasks = new AtomicInteger(0);
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        List<Mod> connectorMods = new java.util.ArrayList<>();

        for (Mod mod : modsToAnalyze) {
            executor.submit(() -> {
                if (isCancelled) return;

                SwingUtilities.invokeLater(() -> currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + mod.getJarName()));

                if (isConnectorRelated(mod, false)) {
                    connectorMods.add(mod);
                    registerDetectedModJar(mod.getJarName());
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
                if (connectorMods.isEmpty()) {
                    appendStyledText(LanguageProvider.get("gui.analysis.connector_detector.no_mods"), NORMAL_COLOR);
                } else {
                    String msg = LanguageProvider.get("gui.analysis.connector_detector.found")
                            .replace("$COUNT$", String.valueOf(connectorMods.size()));
                    appendStyledText(msg, NORMAL_COLOR);
                    for (Mod mod : connectorMods) {
                        appendStyledText(mod.getJarName() + "\n", MOD_COLOR);
                    }
                }
            });
        }
    }

    private boolean isConnectorRelated(Mod mod, boolean recursive) {
        if (Boolean.TRUE.equals(mod.getIsLoadedByConnector())
                || "fabric_api".equals(mod.getModId())
                || "connectormod".equals(mod.getModId())
                || "connector".equals(mod.getModId())) {
            return true;
        }
        if (recursive) return false;
        for (Mod nestedMod : mod.getJarJarMods()) {
            if (isConnectorRelated(nestedMod, true)) {
                return true;
            }
        }
        return false;
    }
}
