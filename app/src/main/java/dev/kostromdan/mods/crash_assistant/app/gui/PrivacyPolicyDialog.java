package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.SwingEDT;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * A dialog for showing and handling the privacy policy acceptance.
 */
public class PrivacyPolicyDialog {

    // Static boolean to track if the dialog should be shown during the current launch
    private static volatile boolean acceptedForCurrentLaunch = false;

    // Synchronization primitives for ensurePrivacyPolicyAccepted()
    private static final Object ensureLock = new Object();
    private static EnsureBatch currentBatch = null;

    private static final class EnsureBatch {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile boolean result = false;
        private volatile boolean decisionReady = false;
        private boolean failed = false;
    }

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
        return SwingEDT.callAndWait(() -> showPrivacyPolicyDialog(null, false));
    }

    private static boolean showPrivacyPolicyDialog(Consumer<Boolean> decisionHandler, boolean checkAcceptance) {
        if (checkAcceptance && isAlreadyAccepted()) {
            if (decisionHandler != null) {
                decisionHandler.accept(true);
            }
            return true;
        }
        JFrame ownerFrame = CrashAssistantGUI.getFrame();

        JDialog dialog = new JDialog(ownerFrame, LanguageProvider.get("gui.privacy.logs_upload_title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        CrashAssistantGUI.setUpIcon(dialog);

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
        final boolean[] decisionReported = {false};
        Consumer<Boolean> reportDecision = accepted -> {
            if (!decisionReported[0]) {
                decisionReported[0] = true;
                if (decisionHandler != null) {
                    decisionHandler.accept(accepted);
                }
            }
        };

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
            reportDecision.accept(true);
        });

        declineButton.addActionListener(e -> {
            result[0] = false;
            dialog.dispose();
            reportDecision.accept(false);
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                reportDecision.accept(result[0]);
            }
        });

        // Show the dialog and wait until it's closed
        dialog.setVisible(true);
        reportDecision.accept(result[0]);

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

        EnsureBatch myBatch;
        boolean iAmFirst;

        synchronized (ensureLock) {
            if (isAlreadyAccepted()) return true;

            if (currentBatch != null) {
                // Dialog is currently showing - join this batch
                myBatch = currentBatch;
                iAmFirst = false;
            } else {
                // No dialog showing - start new batch
                myBatch = new EnsureBatch();
                currentBatch = myBatch;
                iAmFirst = true;
            }
        }

        if (iAmFirst) {
            try {
                SwingEDT.runAndWait(() -> {
                    boolean result = showPrivacyPolicyDialog(
                            accepted -> publishBatchDecision(myBatch, accepted),
                            true
                    );
                    if (!myBatch.decisionReady) {
                        publishBatchDecision(myBatch, result);
                    }
                });
                if (Thread.currentThread().isInterrupted()) {
                    synchronized (myBatch) {
                        myBatch.failed = true;
                        myBatch.result = false;
                        myBatch.decisionReady = true;
                    }
                }
            } catch (Throwable e) {
                try {
                    CrashAssistantApp.LOGGER.error("Error in ensurePrivacyPolicyAccepted", e);
                } finally {
                    synchronized (myBatch) {
                        myBatch.failed = true;
                        myBatch.result = false;
                        myBatch.decisionReady = true;
                    }
                }
            } finally {
                synchronized (ensureLock) {
                    if (currentBatch == myBatch) {
                        currentBatch = null;
                    }
                }
                myBatch.latch.countDown();
            }
        }

        if (SwingUtilities.isEventDispatchThread()) {
            awaitBatchDecisionOnEDT(myBatch);
            return myBatch.result;
        }

        try {
            myBatch.latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return myBatch.result;
    }

    private static void publishBatchDecision(EnsureBatch batch, boolean result) {
        synchronized (batch) {
            if (batch.failed) return;
            batch.result = result;
            batch.decisionReady = true;
        }
        if (!result) {
            // Show ONE declined warning from here
            JOptionPane.showMessageDialog(
                    CrashAssistantGUI.getFrame(),
                    LanguageProvider.get("gui.privacy.declined"),
                    LanguageProvider.get("gui.privacy.title"),
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private static void awaitBatchDecisionOnEDT(EnsureBatch batch) {
        if (batch.decisionReady) return;

        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        Timer timer = new Timer(10, null);
        timer.addActionListener(e -> {
            if (batch.decisionReady) {
                timer.stop();
                loop.exit();
            }
        });
        timer.start();
        if (!loop.enter()) {
            timer.stop();
            throw new IllegalStateException("Could not enter a secondary event loop while awaiting privacy decision");
        }
    }

    /**
     * Resets the privacy consent settings according to the following rules:
     * 1. If privacy.accepted_privacy_info is not null, remove it from local config
     * 2. If acceptedForCurrentLaunch is true, set it to false
     * 3. If none of the above conditions are met, show a notification
     */
    public static void resetPrivacyConsent() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingEDT.runAndWait(PrivacyPolicyDialog::resetPrivacyConsent);
            return;
        }
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
            showConsentResetSuccessDialog(ownerFrame);
        } else {
            JOptionPane.showMessageDialog(
                    ownerFrame,
                    LanguageProvider.get("gui.privacy.consent_not_given"),
                    LanguageProvider.get("gui.privacy.title"),
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private static void showConsentResetSuccessDialog(JFrame ownerFrame) {
        JDialog dialog = new JDialog(ownerFrame, LanguageProvider.get("gui.privacy.title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        CrashAssistantGUI.setUpIcon(dialog);

        JEditorPane messagePane = CrashAssistantGUI.getEditorPane(
                "<strong>" + LanguageProvider.get("gui.privacy.consent_reset_success") + "</strong>\n\n"
                        + LanguageProvider.get("gui.privacy.consent_reset_data_notice") + "\n"
                        + LanguageProvider.get("gui.privacy.consent_reset_manage_question"), true, 520);

        JButton manageLogsButton = new JButton(LanguageProvider.get("gui.menu.privacy.manage_logs"));
        manageLogsButton.setFont(manageLogsButton.getFont().deriveFont(Font.BOLD,
                CrashAssistantConfig.getInteger("gui_customisation.auto_fix_button_font_size")));
        manageLogsButton.setForeground(ControlPanel.deserializeColor(
                CrashAssistantConfig.get("gui_customisation.auto_fix_button_foreground_color"),
                manageLogsButton.getForeground()));
        manageLogsButton.addActionListener(e -> {
            dialog.dispose();
            new LogDeletionDialog(ownerFrame).setVisible(true);
        });

        JButton closeButton = new JButton(LanguageProvider.get("gui.close"));
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel actionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        actionPanel.add(manageLogsButton, gbc);

        JPanel buttonPanel = new JPanel(new BorderLayout(0, 8));
        buttonPanel.add(actionPanel, BorderLayout.CENTER);
        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        closePanel.add(closeButton);
        buttonPanel.add(closePanel, BorderLayout.SOUTH);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 14));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        mainPanel.add(messagePane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(mainPanel);
        dialog.getRootPane().setDefaultButton(manageLogsButton);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(560, dialog.getHeight()));
        dialog.setLocationRelativeTo(ownerFrame);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }
}
