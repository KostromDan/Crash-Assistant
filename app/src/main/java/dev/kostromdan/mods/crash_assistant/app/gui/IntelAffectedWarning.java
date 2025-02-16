package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.IntelCorruptedProcessorChecker;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.Objects;

public class IntelAffectedWarning {
    public static String help_url = "https://www.zdnet.com/article/intel-chip-bug-faq-which-pcs-are-affected-how-to-get-the-patch-and-everything-else-you-need-to-know/";


    public static void showIfAffected() {
        if (!CrashAssistantConfig.getBoolean("intel_corrupted.enabled")) return;
        if (!IntelCorruptedProcessorChecker.isAffectedProcessor()) return;
        boolean showGif = CrashAssistantConfig.getBoolean("intel_corrupted.show_gif");

        ControlPanel.stopMovingToTop = true;

        JDialog dialog = new JDialog((Frame) null, "Intel Chip Bug FAQ", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        int colIndex = 0;

        ImageIcon gif = new ImageIcon(Objects.requireNonNull(IntelAffectedWarning.class.getResource("/assets/intel_bug.gif")));
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

        JEditorPane textPane = CrashAssistantGUI.getEditorPane(
                "<h3>Detected that your CPU is affected by a critical Intel chip bug.</h3>"
                        + "If you are not familiar with this issue, it is highly recommended to read this "
                        + "<a href='" + help_url + "'>FAQ</a>.<br><br>"
                        + "<strong style='color: red;'>DO NOT</strong> report this crash until you have:<br>"
                        + "<ul>"
                        + "<li>Updated your BIOS.</li>"
                        + "<li>Made sure that the crash is not caused by this bug, as the issue won't be "
                        + "resolved even after a BIOS update if the CPU has already started dying.<br>"
                        + "It is important to note that this update will not fix damaged hardware. If the PC "
                        + "has crashed or been unstable due to this issue, the CPU is damaged.</li>"
                        + "<li>If the CPU is damaged, replace it under warranty. Intel has extended the "
                        + "warranty for affected processors by two years.</li>"
                        + "</ul>",
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
        JButton readMoreButton = new JButton("Learn more about this bug");
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
        JCheckBox dontShowAgainCheck = new JCheckBox("Don't show again");
//            bottomPanel.add(dontShowAgainCheck, BorderLayout.WEST);

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
        dialog.setSize(800 - (showGif ? 0 : gif.getIconWidth()), dialog.getPreferredSize().height + (showGif ? 0 : 50));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }


    public static void main(String[] args) {
        IntelAffectedWarning.showIfAffected();
    }
}
