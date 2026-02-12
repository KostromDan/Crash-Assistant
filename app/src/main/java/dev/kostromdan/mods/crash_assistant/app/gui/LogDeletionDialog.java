package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.LinksHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLog;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLogsManager;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.ApiProvider;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.BulkDeletionResult;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.DeletionResult;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LogDeletionDialog extends JDialog {
    private final JPanel listPanel;
    private final JLabel emptyLabel;
    private final Map<UploadedLog, JCheckBox> checkboxes = new HashMap<>();
    private final JButton deleteSelectedButton;
    private final JButton selectAllButton;

    public LogDeletionDialog(Frame owner) {
        super(owner, LanguageProvider.get("gui.log_deletion.title"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        // Match the dimensions of the main GUI
        setSize(owner.getWidth(), owner.getHeight());
        setMinimumSize(new Dimension(owner.getWidth(), owner.getHeight()));
        setLocationRelativeTo(owner);

        JPanel mainPanel = new JPanel(new BorderLayout());
        setContentPane(mainPanel);

        // Help Text & Controls
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JEditorPane helpPane = CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.log_deletion.help"), true);
        helpPane.setBorder(new EmptyBorder(10, 10, 5, 10));
        topPanel.add(helpPane, BorderLayout.NORTH);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        
        selectAllButton = new JButton(LanguageProvider.get("gui.log_deletion.select_all"));
        selectAllButton.addActionListener(e -> toggleSelectAll());
        controlsPanel.add(selectAllButton);
        
        deleteSelectedButton = new JButton(LanguageProvider.get("gui.log_deletion.delete_selected"));
        deleteSelectedButton.addActionListener(e -> deleteSelected());
        controlsPanel.add(deleteSelectedButton);

        topPanel.add(controlsPanel, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

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

    private void toggleSelectAll() {
        boolean anyUnselected = checkboxes.values().stream().anyMatch(cb -> !cb.isSelected());
        CrashAssistantApp.LOGGER.info("User requested to {} all logs ({} items)", anyUnselected ? "select" : "unselect", checkboxes.size());
        for (JCheckBox cb : checkboxes.values()) {
            cb.setSelected(anyUnselected);
        }
        updateSelectAllButtonText();
    }

    private void updateSelectAllButtonText() {
        if (checkboxes.isEmpty()) {
            selectAllButton.setText(LanguageProvider.get("gui.log_deletion.select_all"));
            return;
        }
        long selectedCount = checkboxes.values().stream().filter(JCheckBox::isSelected).count();
        boolean allSelected = selectedCount == checkboxes.size();
        selectAllButton.setText(LanguageProvider.get(allSelected ? "gui.log_deletion.unselect_all" : "gui.log_deletion.select_all"));
    }

    private void deleteSelected() {
        List<UploadedLog> selectedLogs = checkboxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (selectedLogs.isEmpty()) {
            JOptionPane.showMessageDialog(this, LanguageProvider.get("gui.log_deletion.select_first_warning"), "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String msg = LanguageProvider.get("gui.log_deletion.delete_confirm_title") + " (" + selectedLogs.size() + ")";
        
        int confirm = JOptionPane.showConfirmDialog(
                this,
                LanguageProvider.get("gui.log_deletion.delete_selected_confirm_msg").replace("$COUNT$", String.valueOf(selectedLogs.size())),
                msg,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            CrashAssistantApp.LOGGER.info("User cancelled bulk deletion of {} logs", selectedLogs.size());
            return;
        }

        CrashAssistantApp.LOGGER.info("User confirmed bulk deletion of {} logs", selectedLogs.size());

        // Create blocking dialog
        JDialog loadingDialog = new JDialog(this, LanguageProvider.get("gui.log_deletion.status.deleting"), true);
        loadingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        p.add(new JLabel(LanguageProvider.get("gui.log_deletion.status.deleting_count").replace("$COUNT$", String.valueOf(selectedLogs.size())), SwingConstants.CENTER), BorderLayout.CENTER);
        loadingDialog.setContentPane(p);
        loadingDialog.setSize(300, 120);
        loadingDialog.setLocationRelativeTo(this);

        ApiProvider.getMcLogsClient().bulkDeleteLogs(selectedLogs).thenAccept(response -> {
            SwingUtilities.invokeLater(() -> {
                loadingDialog.dispose();
                if (response.isSuccess()) {
                    int deletedCount = 0;
                    List<String> failedIds = new ArrayList<>();
                    
                    for (BulkDeletionResult result : response.getResults()) {
                        if (result.isSuccess() || result.getStatus() == 404) { // 404 is also success in terms of removal
                             UploadedLog log = selectedLogs.stream()
                                    .filter(l -> l.getUrl().endsWith(result.getId()))
                                    .findFirst()
                                    .orElse(null);
                             if (log != null) {
                                 UploadedLogsManager.removeLog(log);
                                 deletedCount++;
                             }
                        } else {
                            failedIds.add(result.getId() + " (" + result.getError() + ")");
                        }
                    }
                    
                    if (failedIds.isEmpty()) {
                        JOptionPane.showMessageDialog(this, LanguageProvider.get("gui.log_deletion.success_count").replace("$COUNT$", String.valueOf(deletedCount)));
                        CrashAssistantApp.LOGGER.info("Bulk deletion successfully finished. Removed {} logs.", deletedCount);
                    } else {
                        String errorMsg = LanguageProvider.get("gui.log_deletion.failed_count").replace("$COUNT$", String.valueOf(failedIds.size())) + "\n\nFailed logs:\n" + String.join("\n", failedIds);
                        CrashAssistantApp.LOGGER.warn("Bulk deletion partially finished. Removed {} logs, failed {}.", deletedCount, failedIds.size());
                        
                        // Limit error message length
                        if (errorMsg.length() > 800) errorMsg = errorMsg.substring(0, 800) + "...";
                        JOptionPane.showMessageDialog(this, errorMsg, "Partial Failure", JOptionPane.WARNING_MESSAGE);
                    }
                    refreshLogs();
                } else {
                     String errorMsg = response.getError();
                     CrashAssistantApp.LOGGER.error("Bulk log deletion critical failure: {}", errorMsg);
                     String userMsg = LanguageProvider.get("gui.log_deletion.failed") + "\n\n" + errorMsg;
                     JOptionPane.showMessageDialog(this, userMsg, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        });

        loadingDialog.setVisible(true);
    }

    private void refreshLogs() {
        listPanel.removeAll();
        checkboxes.clear();
        
        List<UploadedLog> logs = UploadedLogsManager.getSavedLogs();

        if (logs.isEmpty()) {
            listPanel.add(emptyLabel);
            if (selectAllButton != null) selectAllButton.setEnabled(false);
            if (deleteSelectedButton != null) deleteSelectedButton.setEnabled(false);
        } else {
            if (selectAllButton != null) selectAllButton.setEnabled(true);
            if (deleteSelectedButton != null) deleteSelectedButton.setEnabled(true);
            
            // Sort by date descending
            logs.sort((l1, l2) -> Long.compare(l2.getUploadTime(), l1.getUploadTime()));

            for (UploadedLog log : logs) {
                listPanel.add(createLogEntryPanel(log));
                listPanel.add(Box.createVerticalStrut(5));
            }
        }
        
        updateSelectAllButtonText();
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

        // Checkbox
        JCheckBox checkBox = new JCheckBox();
        checkBox.addItemListener(e -> updateSelectAllButtonText());
        checkboxes.put(log, checkBox);
        panel.add(checkBox, BorderLayout.WEST);

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
