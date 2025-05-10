package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
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

        // Check if privacy policy acceptance disabled in config.
        if (!CrashAssistantConfig.getBoolean("general.enable_privacy_policy_acceptance")) {
            return true;
        }

        JFrame ownerFrame = CrashAssistantGUI.getFrame();

        JDialog dialog = new JDialog(ownerFrame, LanguageProvider.get("gui.privacy.logs_upload_title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    
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
        JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.privacy.remember_my_choice"));
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
    
        // Set up the dialog
        dialog.setContentPane(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(ownerFrame);
        dialog.setResizable(false);
    
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
            dialog.dispose();
        });
    
        declineButton.addActionListener(e -> {
            result[0] = false;
            dialog.dispose();
        });
    
        // Show the dialog and wait until it's closed
        dialog.setVisible(true);

        return result[0];
    }

    /**
     * Resets the privacy consent settings according to the following rules:
     * 1. If privacy.accepted_privacy_info is true, remove it from local config
     * 2. If acceptedForCurrentLaunch is true, set it to false
     * 3. If general.enable_privacy_policy_acceptance is false, set it to true
     * 4. If none of the above conditions are met, show a notification
     */
    public static void resetPrivacyConsent() {
        boolean changesApplied = false;

        // Check if privacy.accepted_privacy_info is true and remove it if so
        if (Objects.equals(CrashAssistantLocalConfig.get("privacy.accepted_privacy_info"), true)) {
            CrashAssistantLocalConfig.set("privacy.accepted_privacy_info", null);
            changesApplied = true;
        }

        // Check if acceptedForCurrentLaunch is true and set it to false if so
        if (acceptedForCurrentLaunch) {
            acceptedForCurrentLaunch = false;
            changesApplied = true;
        }

        // Check if general.enable_privacy_policy_acceptance is true and set it to false if so
        if (!CrashAssistantConfig.getBoolean("general.enable_privacy_policy_acceptance")) {
            CrashAssistantConfig.set("general.enable_privacy_policy_acceptance", true);
            changesApplied = true;
        }

        // Get the main GUI frame as owner
        JFrame ownerFrame = CrashAssistantGUI.getFrame();
    
        // Show a notification based on whether changes were applied
        if (changesApplied) {
            JOptionPane.showMessageDialog(
                    ownerFrame,
                    LanguageProvider.get("gui.privacy.consent_reset_success"),
                    LanguageProvider.get("gui.privacy.title"),
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            JOptionPane.showMessageDialog(
                    ownerFrame,
                    LanguageProvider.get("gui.privacy.consent_not_given"),
                    LanguageProvider.get("gui.privacy.title"),
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
}
