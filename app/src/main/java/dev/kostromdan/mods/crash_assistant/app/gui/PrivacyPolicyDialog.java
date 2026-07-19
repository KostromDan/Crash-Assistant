package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * A dialog for showing and handling the privacy policy acceptance.
 */
public class PrivacyPolicyDialog {

    // Static boolean to track if the dialog should be shown during the current launch
    private static boolean acceptedForCurrentLaunch = false;

    // Synchronization primitives for ensurePrivacyPolicyAccepted()
    private static final Object ensureLock = new Object();
    private static CountDownLatch currentBatchLatch = null;
    private static volatile boolean currentBatchResult = false;

    /**
     * Checks whether the privacy policy has already been accepted locally or for the current session.
     */
    private static boolean isAlreadyAccepted() {
        if (Objects.equals(CrashAssistantLocalConfig.get("privacy.accepted_privacy_info"), LanguageProvider.get("gui.privacy.crash_assistant_privacy_policy.version"))) {
            return true;
        }
        if (acceptedForCurrentLaunch) {
            return true;
        }
        return false;
    }

    /**
     * Shows a dialog asking the user to accept the privacy policy for uploading logs.
     * If the user has already accepted the privacy policy, the dialog is not shown and the function returns true.
     *
     * @return true if the user accepts the privacy policy or has already accepted it, false otherwise
     */
    public static boolean showPrivacyPolicyDialog() {
        if (isAlreadyAccepted()) {
            return true;
        }

        JFrame ownerFrame = CrashAssistantGUI.getFrame();

        JDialog dialog = new JDialog(ownerFrame, LanguageProvider.get("gui.privacy.logs_upload_title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Create the question text with a link to the privacy policy
        String question = CrashAssistantLocalConfig.get("privacy.accepted_privacy_info") == null ? "" : LanguageProvider.get("gui.privacy.logs_upload_question_changed") + "\n";
        question += LanguageProvider.get("gui.privacy.logs_upload_question", new HashMap<String, String>() {{
            put("$LINK.PRIVACY_POLICY$", LanguageProvider.get("gui.privacy.privacy_policy"));
        }});
        // Create an editor pane with the question text
        JEditorPane editorPane = CrashAssistantGUI.getEditorPane(question, true);

        // Wrap the editor pane in a panel with a border and padding
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.add(editorPane, BorderLayout.CENTER);

        // Create the "Remember my choice" checkbox
        JCheckBox rememberMyChoiceCheck = new JCheckBox(LanguageProvider.get("gui.privacy.remember_my_choice"));
        rememberMyChoiceCheck.setSelected(true);

        // Create the Accept and Decline buttons
        JButton acceptButton = new JButton(LanguageProvider.get("gui.privacy.logs_upload_accept"));
        acceptButton.setForeground(ControlPanel.deserializeColor(
                CrashAssistantConfig.get("gui_customisation.privacy_policy_yes_button_foreground_color"),
                Color.GREEN.darker()));
        acceptButton.setFont(acceptButton.getFont().deriveFont(Font.BOLD));
        
        JButton declineButton = new JButton(LanguageProvider.get("gui.privacy.logs_upload_decline"));
        declineButton.setForeground(ControlPanel.deserializeColor(
                CrashAssistantConfig.get("gui_customisation.privacy_policy_no_button_foreground_color"),
                Color.RED));
        declineButton.setFont(declineButton.getFont().deriveFont(Font.BOLD));

        // Create a panel for the buttons and checkbox (now on the same level)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(rememberMyChoiceCheck); // Add checkbox to the left of buttons
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
            // Save the user's choice if "Remember my choice" is selected
            if (rememberMyChoiceCheck.isSelected()) {
                CrashAssistantLocalConfig.set("privacy.accepted_privacy_info", LanguageProvider.get("gui.privacy.crash_assistant_privacy_policy.version"));
                CrashAssistantApp.LOGGER.info("User accepted privacy policy.");
            } else {
                // Set the static boolean to true if the user accepted but didn't check "Remember my choice"
                acceptedForCurrentLaunch = true;
                CrashAssistantApp.LOGGER.info("User accepted privacy policy (without checking \"Remember my choice\" checkbox).");
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
     * Thread-safe blocking method to ensure the privacy policy is accepted.
     * Multiple threads can call this simultaneously - only the first will show the dialog,
     * others will block and wait. On accept all get true; on decline a single warning is
     * shown from here and all get false.
     *
     * @return true if accepted, false if declined
     */
    public static boolean ensurePrivacyPolicyAccepted() {
        if (isAlreadyAccepted()) return true;

        CountDownLatch myLatch;
        boolean iAmFirst;

        synchronized (ensureLock) {
            if (isAlreadyAccepted()) return true;

            if (currentBatchLatch != null) {
                // Dialog is currently showing - join this batch
                myLatch = currentBatchLatch;
                iAmFirst = false;
            } else {
                // No dialog showing - start new batch
                currentBatchLatch = new CountDownLatch(1);
                myLatch = currentBatchLatch;
                iAmFirst = true;
            }
        }

        if (iAmFirst) {
            try {
                final boolean[] result = {false};
                SwingUtilities.invokeAndWait(() -> {
                    result[0] = showPrivacyPolicyDialog();
                    if (!result[0]) {
                        // Show ONE declined warning from here
                        JOptionPane.showMessageDialog(
                                CrashAssistantGUI.getFrame(),
                                LanguageProvider.get("gui.privacy.declined"),
                                LanguageProvider.get("gui.privacy.title"),
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                });
                currentBatchResult = result[0];
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error in ensurePrivacyPolicyAccepted", e);
                currentBatchResult = false;
            } finally {
                synchronized (ensureLock) {
                    currentBatchLatch = null;
                }
                myLatch.countDown();
            }
        }

        try {
            myLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return currentBatchResult;
    }

    /**
     * Resets the privacy consent settings according to the following rules:
     * 1. If privacy.accepted_privacy_info is not null, remove it from local config
     * 2. If acceptedForCurrentLaunch is true, set it to false
     * 3. If none of the above conditions are met, show a notification
     */
    public static void resetPrivacyConsent() {
        boolean changesApplied = false;

        // Check if privacy.accepted_privacy_info is not null and remove it if so
        if (CrashAssistantLocalConfig.get("privacy.accepted_privacy_info") != null) {
            CrashAssistantLocalConfig.set("privacy.accepted_privacy_info", null);
            changesApplied = true;
        }

        // Check if acceptedForCurrentLaunch is true and set it to false if so
        if (acceptedForCurrentLaunch) {
            acceptedForCurrentLaunch = false;
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
