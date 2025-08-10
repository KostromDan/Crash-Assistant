package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MCreatorModDetectorGUI extends AnalysisGUIBase {

    public MCreatorModDetectorGUI(JFrame parent) {
        super(parent, "MCreator Mod Detector", "This tool scans for mods made with MCreator.");
    }

    public static void showMCreatorModDetectorDialog(JFrame parent) {
        new MCreatorModDetectorGUI(parent).start();
    }

    @Override
    protected void performAnalysis() {
        LinkedHashSet<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true);
        int totalMods = modsToAnalyze.size();
        AtomicInteger completedTasks = new AtomicInteger(0);
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        List<Mod> mcreatorMods = new java.util.ArrayList<>();

        for (Mod mod : modsToAnalyze) {
            executor.submit(() -> {
                if (isCancelled) return;

                SwingUtilities.invokeLater(() -> currentJarLabel.setText("Current mod: " + mod.getJarName()));

                if (Boolean.TRUE.equals(mod.IsMCreator())) {
                    mcreatorMods.add(mod);
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
                if (mcreatorMods.isEmpty()) {
                    appendStyledText("No MCreator mods found.\n", NORMAL_COLOR);
                } else {
                    appendStyledText("Found " + mcreatorMods.size() + " MCreator mod(s):\n", NORMAL_COLOR);
                    for (Mod mod : mcreatorMods) {
                        appendStyledText(mod.getJarName() + "\n", MOD_COLOR);
                    }
                }
            });
        }
    }


    @Override
    protected void addOkButton() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton whyButton = new JButton("Why are MCreator mods often discouraged?");
        whyButton.addActionListener(e -> {
            String infoMessage = "MCreator is a software used to create Minecraft mods without programming knowledge. "
                    + "However, mods produced with it often have issues that can cause problems in larger modpacks:\n\n"
                    + "- The generated code can be inefficient, sometimes wrapping and unwrapping code for no reason.\n"
                    + "- It may use reflection unnecessarily, which can be slow and brittle.\n"
                    + "- The generated code can sometimes cause issues with other mods, especially in complex areas like world generation.\n"
                    + "- The code structure can be nonsensical, using nested classes and annotations in confusing ways.\n"
                    + "- It often doesn't follow standard Forge or Fabric coding practices, making it harder for other developers to ensure compatibility.\n"
                    + "- The code can be very difficult for a human to read and debug.\n"
                    + "- If a crash occurs, or if a feature is needed that MCreator doesn't support, it can be very difficult for the author to fix or extend the mod without rewriting it from scratch.";

            JEditorPane infoPane = dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI.getEditorPane(infoMessage, true, 500);

            JOptionPane.showMessageDialog(
                    dialog,
                    infoPane,
                    "About MCreator Mods",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
        buttonPanel.add(whyButton);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.revalidate();
    }
}