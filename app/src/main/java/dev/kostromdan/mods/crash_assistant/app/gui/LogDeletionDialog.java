package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.LinksHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLog;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLogsManager;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.ApiProvider;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.DeletionResult;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LogDeletionDialog extends JDialog {
    private final JPanel listPanel;
    private final JLabel emptyLabel;

    public LogDeletionDialog(Frame owner) {
        super(owner, LanguageProvider.get("gui.log_deletion.title"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        // Match the dimensions of the main GUI
        setSize(owner.getWidth(), owner.getHeight());
        setMinimumSize(new Dimension(owner.getWidth(), owner.getHeight()));
        setLocationRelativeTo(owner);

        JPanel mainPanel = new JPanel(new BorderLayout());
        setContentPane(mainPanel);

        // Help Text
        JEditorPane helpPane = CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.log_deletion.help"), true);
        helpPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.add(helpPane, BorderLayout.NORTH);

        // List Panel
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        
        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        emptyLabel = new JLabel(LanguageProvider.get("gui.log_deletion.empty"), SwingConstants.CENTER);
        emptyLabel.setBorder(new EmptyBorder(20, 20, 20, 20));

        refreshLogs();
    }

    private void refreshLogs() {
        listPanel.removeAll();
        List<UploadedLog> logs = UploadedLogsManager.getSavedLogs();

        if (logs.isEmpty()) {
            listPanel.add(emptyLabel);
        } else {
            // Sort by date descending
            logs.sort((l1, l2) -> Long.compare(l2.getUploadTime(), l1.getUploadTime()));

            for (UploadedLog log : logs) {
                listPanel.add(createLogEntryPanel(log));
                listPanel.add(Box.createVerticalStrut(5));
            }
        }
        
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createLogEntryPanel(UploadedLog log) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(5, 5, 5, 5)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        // Info Panel
        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        JLabel nameLabel = new JLabel("<html><b>" + log.getName() + "</b></html>");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        JLabel dateLabel = new JLabel(sdf.format(new Date(log.getUploadTime())));
        dateLabel.setForeground(Color.GRAY);
        
        infoPanel.add(nameLabel);
        infoPanel.add(dateLabel);
        
        panel.add(infoPanel, BorderLayout.CENTER);

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        JButton openButton = new JButton(LanguageProvider.get("gui.log_deletion.open"));
        openButton.addActionListener(e -> {
            try {
                CrashAssistantApp.LOGGER.info("User requested to open log in browser: {}", log.getUrl());
                LinksHelper.browse(new URI(log.getUrl()));
            } catch (Exception ex) {
                CrashAssistantApp.LOGGER.error("Failed to open log URL", ex);
            }
        });

        JButton deleteButton = new JButton(LanguageProvider.get("gui.log_deletion.delete"));
        deleteButton.addActionListener(e -> handleDelete(log));

        buttonPanel.add(openButton);
        buttonPanel.add(deleteButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private void handleDelete(UploadedLog log) {
        CrashAssistantApp.LOGGER.info("User requested to delete log: {} ({})", log.getName(), log.getUrl());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String msg = LanguageProvider.get("gui.log_deletion.delete_confirm_msg")
                .replace("$NAME$", log.getName())
                .replace("$DATE$", sdf.format(new Date(log.getUploadTime())));
        
        int confirm = JOptionPane.showConfirmDialog(
                this,
                msg,
                LanguageProvider.get("gui.log_deletion.delete_confirm_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        // Create blocking dialog
        JDialog loadingDialog = new JDialog(this, LanguageProvider.get("gui.log_deletion.status.deleting"), true);
        loadingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        p.add(new JLabel(LanguageProvider.get("gui.log_deletion.status.deleting"), SwingConstants.CENTER), BorderLayout.CENTER);
        loadingDialog.setContentPane(p);
        loadingDialog.setSize(200, 100);
        loadingDialog.setLocationRelativeTo(this);

        String id = log.getUrl().substring(log.getUrl().lastIndexOf('/') + 1);

        ApiProvider.getMcLogsClient().deleteLog(id, log.getDeleteToken()).thenAccept(response -> {
            SwingUtilities.invokeLater(() -> {
                loadingDialog.dispose();
                if (response.getResult() == DeletionResult.SUCCESS) {
                    CrashAssistantApp.LOGGER.info("Log deletion successful: {}", log.getUrl());
                    UploadedLogsManager.removeLog(log);
                    JOptionPane.showMessageDialog(this, LanguageProvider.get("gui.log_deletion.success"));
                    refreshLogs();
                } else if (response.getResult() == DeletionResult.NOT_FOUND) {
                    CrashAssistantApp.LOGGER.warn("Log deletion failed (Not Found): {}", log.getUrl());
                    UploadedLogsManager.removeLog(log);
                    JOptionPane.showMessageDialog(this, LanguageProvider.get("gui.log_deletion.not_found"), "Warning", JOptionPane.WARNING_MESSAGE);
                    refreshLogs();
                } else {
                    String errorMsg = response.getMessage();
                    CrashAssistantApp.LOGGER.error("Log deletion failed: {} - {}", log.getUrl(), errorMsg);
                    String userMsg = LanguageProvider.get("gui.log_deletion.failed") + "\n\n" + errorMsg;
                    JOptionPane.showMessageDialog(this, userMsg, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        
        loadingDialog.setVisible(true);
    }
}
