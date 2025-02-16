package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URI;

public class IntelAffectedWarning {
    public static String help_url = "https://www.zdnet.com/article/intel-chip-bug-faq-which-pcs-are-affected-how-to-get-the-patch-and-everything-else-you-need-to-know/";


    public static void showIfAffected() {
//        if (!IntelCorruptedProcessorChecker.isAffectedProcessor()) {
//            return;
//        }

        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog((Frame) null, "Intel Chip Bug FAQ", true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout(10, 10));
            dialog.setResizable(false);

            JLabel gifLabel = new JLabel("", SwingConstants.CENTER);
            gifLabel.setPreferredSize(new Dimension(211, 374));
            gifLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            gifLabel.setIcon(new ImageIcon(IntelAffectedWarning.class.getResource("/assets/intel_bug.gif")));
            dialog.add(gifLabel, BorderLayout.WEST);

            JPanel textPanel = new JPanel(new BorderLayout());
            JEditorPane pane = CrashAssistantGUI.getEditorPane(
                    ("<h3>Detected that your CPU is affected by a critical Intel chip bug.</h3>" +
                            "If you are not familiar with this issue, it is highly recommended to read this <a href = $LINK$>FAQ</a>.\n" +
                            "<strong style='color: red;'>DO NOT</strong> report this crash until you have:\n" +
                            "<ul>" +
                            "<li>Updated your BIOS.</li>" +
                            "<li>Made sure that the crash is not caused by this bug, as the issue won't be resolved even after a BIOS update if the CPU has already started dying.\n" +
                            "It is important to note that this update will not fix damaged hardware. If the PC has crashed or been unstable due to this issue, the CPU is damaged.</li>" +
                            "<li>If the CPU is damaged, replace it under warranty. Intel has extended the warranty for affected processors by two years.</li>" +
                            "</ul>").replace("$LINK$", help_url),
                    true
            );

            textPanel.add(pane, BorderLayout.CENTER);
            dialog.add(textPanel, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

            JButton readMoreButton = new JButton("Learn more about this bug");
            readMoreButton.addActionListener((ActionEvent e) -> {
                try {
                    Desktop.getDesktop().browse(new URI(help_url));
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.error("Error opening URL: ", ex);
                }

            });
            buttonPanel.add(readMoreButton);

            JButton okButton = new JButton("OK");
            okButton.addActionListener(e -> dialog.dispose());
            buttonPanel.add(okButton);

            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack();
            dialog.setSize(700, dialog.getPreferredSize().height);
            dialog.setLocationRelativeTo(null);

            dialog.setVisible(true);
        });
    }

    public static void main(String[] args) {
        IntelAffectedWarning.showIfAffected();
    }
}
