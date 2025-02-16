package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URI;

public class IntelAffectedWarning {
    public static String help_url = "https://www.zdnet.com/article/intel-chip-bug-faq-which-pcs-are-affected-how-to-get-the-patch-and-everything-else-you-need-to-know/";


    public static void showIfAffected(boolean showGif) {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog((Frame) null, "Intel Chip Bug FAQ", true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            // Use GridBagLayout for full control
            JPanel mainPanel = new JPanel(new GridBagLayout());
            // Adjust outer padding as desired
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints gbc = new GridBagConstraints();
            // Smaller insets so buttons/text are closer
            gbc.insets = new Insets(3, 3, 3, 3);

            int colIndex = 0; // Which column we'll use for text & buttons

            // ---------------------------------------------------------
            // 1) Optionally add the GIF on the left, spanning top->bottom
            // ---------------------------------------------------------
            if (showGif) {
                JLabel gifLabel = new JLabel(new ImageIcon(
                        IntelAffectedWarning.class.getResource("/assets/intel_bug.gif")
                ));
                gifLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

                // Place GIF in the left column, spanning 2 rows
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.gridheight = 2;         // spans both "top" & "bottom" rows
                gbc.weightx = 0;
                gbc.weighty = 1.0;          // let it expand vertically
                gbc.fill = GridBagConstraints.VERTICAL;
                gbc.anchor = GridBagConstraints.CENTER;
                mainPanel.add(gifLabel, gbc);

                // Next content goes into the next column
                colIndex = 1;
            }

            // ---------------------------------------------------------
            // 2) Text in the top cell (right if GIF is shown, left if not)
            // ---------------------------------------------------------
            JEditorPane textPane = CrashAssistantGUI.getEditorPane(
                    "<h3>Detected that your CPU is affected by a critical Intel chip bug.</h3>" +
                            "If you are not familiar with this issue, it is highly recommended to read this " +
                            "<a href='" + help_url + "'>FAQ</a>.<br><br>" +
                            "<strong style='color: red;'>DO NOT</strong> report this crash until you have:<br>" +
                            "<ul>" +
                            "<li>Updated your BIOS.</li>" +
                            "<li>Made sure that the crash is not caused by this bug, as the issue won't be " +
                            "resolved even after a BIOS update if the CPU has already started dying.<br>" +
                            "It is important to note that this update will not fix damaged hardware. If the PC " +
                            "has crashed or been unstable due to this issue, the CPU is damaged.</li>" +
                            "<li>If the CPU is damaged, replace it under warranty. Intel has extended the " +
                            "warranty for affected processors by two years.</li>" +
                            "</ul>",
                    true
            );
            JScrollPane scrollPane = new JScrollPane(textPane);

            gbc.gridx = colIndex;    // same column if no GIF, or next column if GIF is shown
            gbc.gridy = 0;           // top row
            gbc.gridheight = 1;      // just that row
            gbc.weightx = 1.0;       // allow horizontal expansion
            gbc.weighty = 1.0;       // allow vertical expansion
            gbc.fill = GridBagConstraints.BOTH;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(scrollPane, gbc);

            // ---------------------------------------------------------
            // 3) Buttons in bottom row, anchored to the right
            // ---------------------------------------------------------
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
            // If you want them truly at the far right, use FlowLayout.RIGHT instead
            // e.g. new FlowLayout(FlowLayout.RIGHT, 5, 5)

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

            gbc.gridx = colIndex;
            gbc.gridy = 1;           // bottom row
            gbc.gridheight = 1;
            gbc.weightx = 0;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.NONE;
            // Anchor the button panel to the EAST so it’s near the right edge
            gbc.anchor = GridBagConstraints.EAST;
            mainPanel.add(buttonPanel, gbc);

            // ---------------------------------------------------------
            // Final dialog setup
            // ---------------------------------------------------------
            dialog.setContentPane(mainPanel);
            dialog.pack();
            // Optionally adjust width; the height is from pack()
            dialog.setSize(700, dialog.getPreferredSize().height);
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        });
    }



    public static void main(String[] args) {
        IntelAffectedWarning.showIfAffected(true);
    }
}
