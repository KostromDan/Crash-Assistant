package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Objects;

/**
 * A dialog for showing and handling the privacy policy acceptance.
 */
public class PrivacyPolicyDialog {
    
    // Static boolean to track if the dialog should be shown during the current launch
    private static boolean acceptedForCurrentLaunch = false;
    
    /**
     * Shows a dialog asking the user to accept the privacy policy for uploading logs.
     * If the user has already accepted the privacy policy, the dialog is not shown and the function returns true.
     *
     * @return true if the user accepts the privacy policy or has already accepted it, false otherwise
     */
    public static boolean showPrivacyPolicyDialog() {
        // Check if the user has already accepted the privacy policy
        if (Objects.equals(CrashAssistantLocalConfig.get("privacy.accepted_privacy_info"), true)) {
            return true;
        }
        
        // Check if the user has accepted for the current launch
        if (acceptedForCurrentLaunch) {
            return true;
        }

        // Create a new JFrame for the dialog
        JFrame dialogFrame = new JFrame(LanguageProvider.get("gui.privacy.logs_upload_title"));

        // Create the question text with a link to the privacy policy
        String question = LanguageProvider.get("gui.privacy.logs_upload_question", new HashMap<String, String>() {{
            put("$LINK.PRIVACY_POLICY$", LanguageProvider.get("gui.privacy.privacy_policy"));
        }});
        // Create an editor pane with the question text
        JEditorPane editorPane = CrashAssistantGUI.getEditorPane(question, true);

        // Wrap the editor pane in a panel with a border and padding
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.add(editorPane, BorderLayout.CENTER);

        // Create the "Don't show again" checkbox
        JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
        dontShowAgainCheck.setSelected(true);

        // Create the Accept and Decline buttons
        JButton acceptButton = new JButton(LanguageProvider.get("gui.privacy.logs_upload_accept"));
        JButton declineButton = new JButton(LanguageProvider.get("gui.privacy.logs_upload_decline"));

        // Create a panel for the buttons and checkbox (now on the same level)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(dontShowAgainCheck); // Add checkbox to the left of buttons
        buttonPanel.add(acceptButton);
        buttonPanel.add(declineButton);

        // Create the main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(textPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Set up the dialog frame
        dialogFrame.setContentPane(mainPanel);
        dialogFrame.pack();
        dialogFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        dialogFrame.setLocationRelativeTo(null);

        // Create a variable to store the result
        final boolean[] result = {false};

        // Add action listeners to the buttons
        acceptButton.addActionListener(e -> {
            // Save the user's choice if "Don't show again" is selected
            if (dontShowAgainCheck.isSelected()) {
                CrashAssistantLocalConfig.set("privacy.accepted_privacy_info", true);
            } else {
                // Set the static boolean to true if the user accepted but didn't check "Don't show again"
                acceptedForCurrentLaunch = true;
            }
            result[0] = true;
            dialogFrame.dispose();
        });

        declineButton.addActionListener(e -> {
            result[0] = false;
            dialogFrame.dispose();
        });

        // Show the dialog
        dialogFrame.setVisible(true);

        // Wait for the dialog to be closed
        while (dialogFrame.isVisible()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return result[0];
    }
}