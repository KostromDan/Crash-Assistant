package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.IntelCorruptedProcessorChecker;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.Objects;

public class IntelChipBugWarning {
    public static final String help_url = "https://www.zdnet.com/article/intel-chip-bug-faq-which-pcs-are-affected-how-to-get-the-patch-and-everything-else-you-need-to-know/";


    public static void showIfAffected(boolean debug) {
        if (!CrashAssistantConfig.getBoolean("intel_corrupted.enabled")) return;
        if (!IntelCorruptedProcessorChecker.isAffectedProcessor() && !debug) return;
        if (Objects.equals(CrashAssistantLocalConfig.get("intel_corrupted.dont_show_again"), true)) return;
        boolean showGif = CrashAssistantConfig.getBoolean("intel_corrupted.show_gif");

        ControlPanel.stopMovingToTop = true;

        JDialog dialog = new JDialog((Frame) null, LanguageProvider.get("gui.intel_corrupted_title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        int colIndex = 0;

        ImageIcon gif = new ImageIcon(Objects.requireNonNull(IntelChipBugWarning.class.getResource("/assets/intel_bug.gif")));
        if (showGif) {
            JLabel gifLabel = new JLabel(gif);
            gifLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridheight = 2;
            gbc.weightx = 0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.VERTICAL;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(gifLabel, gbc);

            colIndex = 1;
        }

        JEditorPane textPane = CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.intel_corrupted_msg").replace("$HELP_URL$", help_url),
                true
        );
        textPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));


        gbc.gridx = colIndex;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(textPane, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        JButton readMoreButton = new JButton(LanguageProvider.get("gui.intel_corrupted_read_more"));
        readMoreButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(help_url));
            } catch (Exception ex) {
                CrashAssistantApp.LOGGER.error("Error opening URL: ", ex);
            }
        });
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(readMoreButton);
        buttonPanel.add(okButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
        dontShowAgainCheck.addActionListener(e -> {
            CrashAssistantLocalConfig.set("intel_corrupted.dont_show_again", dontShowAgainCheck.isSelected());
        });
        bottomPanel.add(dontShowAgainCheck, BorderLayout.WEST);

        gbc.gridx = colIndex;
        gbc.gridy = 1;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(bottomPanel, gbc);

        dialog.setContentPane(mainPanel);
        dialog.pack();
        dialog.setSize(800 - (showGif ? 0 : gif.getIconWidth()), Math.max(dialog.getPreferredSize().height,gif.getIconHeight()));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }


    public static void main(String[] args) {
        IntelChipBugWarning.showIfAffected(true);
    }
}
