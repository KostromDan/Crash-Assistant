package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;

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
        executor.shutdown();
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        List<String> connectorModJarNames = new java.util.ArrayList<>();
        int completedTasks = 0;

        for (Mod mod : modsToAnalyze) {
            if (isCancelled) {
                return;
            }

            String jarName = mod.getJarName();
            SwingUtilities.invokeLater(() -> currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + jarName));

            if (isConnectorRelated(mod, false)) {
                connectorModJarNames.add(jarName);
                registerDetectedModJar(jarName);
            }

            completedTasks++;
            int completed = completedTasks;
            SwingUtilities.invokeLater(() -> {
                if (!isCancelled) {
                    progressBar.setValue(completed);
                }
            });
        }

        if (!isCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (connectorModJarNames.isEmpty()) {
                    appendStyledText(LanguageProvider.get("gui.analysis.connector_detector.no_mods"), NORMAL_COLOR);
                } else {
                    String msg = LanguageProvider.get("gui.analysis.connector_detector.found")
                            .replace("$COUNT$", String.valueOf(connectorModJarNames.size()));
                    appendStyledText(msg, NORMAL_COLOR);
                    for (String jarName : connectorModJarNames) {
                        appendStyledText(jarName + "\n", MOD_COLOR);
                    }
                }
            });
        }
    }

    private boolean isConnectorRelated(Mod mod, boolean recursive) {
        if (Boolean.TRUE.equals(mod.getIsLoadedByConnector())
                || "fabric_api".equals(mod.getModId())
                || "connectorextras".equals(mod.getModId())
                || "connectormod".equals(mod.getModId())
                || "connector".equals(mod.getModId())) {
            return true;
        }
        if (recursive) return false;
        List<Mod> nestedMods = mod.getJarJarMods();
        if (nestedMods == null || nestedMods.isEmpty()) {
            return false;
        }
        for (Mod nestedMod : nestedMods) {
            if (isConnectorRelated(nestedMod, true)) {
                return true;
            }
        }
        return false;
    }
}
