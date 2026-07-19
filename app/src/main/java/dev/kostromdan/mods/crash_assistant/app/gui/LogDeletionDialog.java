package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.LinksHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.MclogArrayRegistrar;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLog;
import dev.kostromdan.mods.crash_assistant.app.utils.UploadedLogsManager;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.ApiProvider;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.BulkDeletionResult;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.BulkLogDeletionResponse;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.MetadataDeletionResponse;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class LogDeletionDialog extends JDialog {
    private enum DeleteTarget {
        MCLOGS,
        METADATA,
        BOTH
    }

    private final JPanel listPanel;
    private final JLabel emptyLabel;
    private final Map<UploadedLog, JCheckBox> checkboxes = new HashMap<>();
    private final JButton selectAllButton;
    private final JButton deleteSelectedMcLogsButton;
    private final JButton deleteSelectedMetadataButton;
    private final JButton deleteSelectedBothButton;

    public LogDeletionDialog(Frame owner) {
        super(owner, LanguageProvider.get("gui.log_deletion.title"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int defaultWidth = Math.min(Math.max(owner.getWidth(), 1200), screenBounds.width);
        int defaultHeight = Math.min(Math.max(owner.getHeight(), 650), screenBounds.height);
        setSize(defaultWidth, defaultHeight);
        setMinimumSize(new Dimension(defaultWidth, defaultHeight));
        setLocationRelativeTo(owner);

        JPanel mainPanel = new JPanel(new BorderLayout());
        setContentPane(mainPanel);

        JPanel topPanel = new JPanel(new BorderLayout());
        JEditorPane helpPane = CrashAssistantGUI.getEditorPane(
                LanguageProvider.get("gui.log_deletion.help_v2"), true);
        helpPane.setBorder(new EmptyBorder(10, 10, 5, 10));
        topPanel.add(helpPane, BorderLayout.NORTH);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        selectAllButton = new JButton(LanguageProvider.get("gui.log_deletion.select_all"));
        selectAllButton.addActionListener(event -> toggleSelectAll());
        controlsPanel.add(selectAllButton);

        deleteSelectedMcLogsButton = createBulkButton(
                "gui.log_deletion.delete_selected_mclogs",
                DeleteTarget.MCLOGS);
        deleteSelectedMetadataButton = createBulkButton(
                "gui.log_deletion.delete_selected_metadata",
                DeleteTarget.METADATA);
        deleteSelectedBothButton = createBulkButton(
                "gui.log_deletion.delete_selected_both",
                DeleteTarget.BOTH);
        controlsPanel.add(deleteSelectedMcLogsButton);
        controlsPanel.add(deleteSelectedMetadataButton);
        controlsPanel.add(deleteSelectedBothButton);

        topPanel.add(controlsPanel, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        emptyLabel = new JLabel(LanguageProvider.get("gui.log_deletion.empty"), SwingConstants.CENTER);
        emptyLabel.setBorder(new EmptyBorder(20, 20, 20, 20));

        refreshLogs();
    }

    private JButton createBulkButton(String languageKey, DeleteTarget target) {
        JButton button = new JButton(LanguageProvider.get(languageKey));
        button.addActionListener(event -> delete(getSelectedLogs(), target));
        return button;
    }

    private void toggleSelectAll() {
        boolean select = checkboxes.values().stream().anyMatch(checkbox -> !checkbox.isSelected());
        for (JCheckBox checkbox : checkboxes.values()) {
            checkbox.setSelected(select);
        }
        updateBulkButtons();
    }

    private List<UploadedLog> getSelectedLogs() {
        return checkboxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private void updateBulkButtons() {
        List<UploadedLog> selected = getSelectedLogs();
        boolean hasSelection = !selected.isEmpty();
        boolean canDeleteMcLogs = selected.stream().anyMatch(UploadedLog::canDeleteFromMcLogs);
        boolean canDeleteMetadata = selected.stream().anyMatch(UploadedLog::canDeleteMetadata);

        selectAllButton.setEnabled(!checkboxes.isEmpty());
        selectAllButton.setText(LanguageProvider.get(
                hasSelection && selected.size() == checkboxes.size()
                        ? "gui.log_deletion.unselect_all"
                        : "gui.log_deletion.select_all"));
        deleteSelectedMcLogsButton.setEnabled(canDeleteMcLogs);
        deleteSelectedMetadataButton.setEnabled(canDeleteMetadata);
        deleteSelectedBothButton.setEnabled(canDeleteMcLogs || canDeleteMetadata);

        String selectFirst = LanguageProvider.get("gui.log_deletion.tooltip.select_first");
        deleteSelectedMcLogsButton.setToolTipText(canDeleteMcLogs
                ? null
                : hasSelection
                        ? LanguageProvider.get("gui.log_deletion.tooltip.mclogs_already_deleted")
                        : selectFirst);
        deleteSelectedMetadataButton.setToolTipText(canDeleteMetadata
                ? null
                : hasSelection ? metadataUnavailableTooltip(selected) : selectFirst);
        deleteSelectedBothButton.setToolTipText(
                canDeleteMcLogs || canDeleteMetadata ? null : selectFirst);
    }

    private void delete(List<UploadedLog> requestedLogs, DeleteTarget target) {
        List<UploadedLog> mcLogs = target == DeleteTarget.METADATA
                ? Collections.emptyList()
                : requestedLogs.stream()
                        .filter(UploadedLog::canDeleteFromMcLogs)
                        .collect(Collectors.toList());
        Set<String> metadataTokens = target == DeleteTarget.MCLOGS
                ? Collections.emptySet()
                : requestedLogs.stream()
                        .filter(UploadedLog::canDeleteMetadata)
                        .map(UploadedLog::getArrayToken)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (mcLogs.isEmpty() && metadataTokens.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    LanguageProvider.get("gui.log_deletion.select_first_warning"),
                    LanguageProvider.get("gui.log_deletion.warning_title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String targetName = LanguageProvider.get("gui.log_deletion.target." + target.name().toLowerCase());
        String confirmation = LanguageProvider.get("gui.log_deletion.delete_operation_confirm")
                .replace("$COUNT$", String.valueOf(requestedLogs.size()))
                .replace("$TARGET$", targetName);
        int choice = JOptionPane.showConfirmDialog(
                this,
                confirmation,
                LanguageProvider.get("gui.log_deletion.delete_confirm_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        JDialog loadingDialog = createLoadingDialog(requestedLogs.size());
        CompletableFuture<BulkLogDeletionResponse> mcLogsFuture = mcLogs.isEmpty()
                ? CompletableFuture.completedFuture(new BulkLogDeletionResponse(true, new ArrayList<>(), null))
                : ApiProvider.getMcLogsClient().bulkDeleteLogs(mcLogs);
        CompletableFuture<MetadataDeletionResponse> metadataFuture = metadataTokens.isEmpty()
                ? CompletableFuture.completedFuture(new MetadataDeletionResponse(true, 0, 0, null))
                : MclogArrayRegistrar.waitForPendingRegistrations()
                        .thenCompose(ignored -> ApiProvider.getMetadataClient().bulkDelete(metadataTokens));

        mcLogsFuture.thenCombine(metadataFuture, DeleteOperationResult::new).whenComplete((result, throwable) ->
                SwingUtilities.invokeLater(() -> {
                    loadingDialog.dispose();
                    if (throwable == null) {
                        applyResult(result, metadataTokens);
                    } else {
                        showUnexpectedFailure(throwable);
                    }
                }));
        loadingDialog.setVisible(true);
    }

    private JDialog createLoadingDialog(int count) {
        JDialog dialog = new JDialog(this, LanguageProvider.get("gui.log_deletion.status.deleting"), true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel(
                LanguageProvider.get("gui.log_deletion.status.deleting_count")
                        .replace("$COUNT$", String.valueOf(count)),
                SwingConstants.CENTER), BorderLayout.CENTER);
        dialog.setContentPane(panel);
        dialog.setSize(340, 120);
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private void applyResult(DeleteOperationResult result, Set<String> metadataTokens) {
        Set<String> deletedLogIds = new LinkedHashSet<>();
        List<String> failures = new ArrayList<>();

        if (!result.mcLogsResponse.isSuccess()) {
            failures.add(errorOrUnknown(result.mcLogsResponse.getError()));
        } else {
            for (BulkDeletionResult deletionResult : result.mcLogsResponse.getResults()) {
                if (deletionResult.isSuccess() || deletionResult.getStatus() == 404) {
                    deletedLogIds.add(deletionResult.getId());
                } else {
                    failures.add(deletionResult.getId() + ": " + errorOrUnknown(deletionResult.getError()));
                }
            }
        }
        if (!deletedLogIds.isEmpty()) {
            UploadedLogsManager.markDeletedFromMcLogs(deletedLogIds);
        }

        int deletedMetadataGroups = 0;
        if (result.metadataResponse.isSuccess()) {
            if (!metadataTokens.isEmpty()) {
                UploadedLogsManager.markMetadataDeleted(metadataTokens);
                deletedMetadataGroups = metadataTokens.size();
            }
        } else {
            failures.add(errorOrUnknown(result.metadataResponse.getError()));
        }

        refreshLogs();
        String message = LanguageProvider.get("gui.log_deletion.operation_result")
                .replace("$LOGS$", String.valueOf(deletedLogIds.size()))
                .replace("$GROUPS$", String.valueOf(deletedMetadataGroups));
        if (!failures.isEmpty()) {
            message += "\n\n" + LanguageProvider.get("gui.log_deletion.operation_failures")
                    .replace("$COUNT$", String.valueOf(failures.size()))
                    + "\n" + String.join("\n", failures);
            if (message.length() > 1200) {
                message = message.substring(0, 1200) + "...";
            }
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    LanguageProvider.get("gui.log_deletion.partial_failure_title"),
                    JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, message);
        }
    }

    private void refreshLogs() {
        listPanel.removeAll();
        checkboxes.clear();

        List<UploadedLog> logs = UploadedLogsManager.getSavedLogs();
        if (logs.isEmpty()) {
            listPanel.add(emptyLabel);
        } else {
            logs.sort((left, right) -> Long.compare(right.getUploadTime(), left.getUploadTime()));
            for (UploadedLog log : logs) {
                listPanel.add(createLogEntryPanel(log));
                listPanel.add(Box.createVerticalStrut(5));
            }
        }

        updateBulkButtons();
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createLogEntryPanel(UploadedLog log) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(5, 5, 5, 5)));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        JCheckBox checkBox = new JCheckBox();
        checkBox.addItemListener(event -> updateBulkButtons());
        checkboxes.put(log, checkBox);
        panel.add(checkBox, BorderLayout.WEST);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        panel.add(new JLabel("<html><b>" + escapeHtml(log.getName()) + "</b> — "
                + dateFormat.format(new Date(log.getUploadTime())) + "</html>"), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton openButton = new JButton(LanguageProvider.get("gui.log_deletion.open"));
        openButton.addActionListener(event -> open(log));

        JButton deleteMcLogsButton = createRowDeleteButton(
                "gui.log_deletion.delete_mclogs", log, DeleteTarget.MCLOGS);
        deleteMcLogsButton.setEnabled(log.canDeleteFromMcLogs());
        if (!log.canDeleteFromMcLogs()) {
            deleteMcLogsButton.setToolTipText(LanguageProvider.get(
                    "gui.log_deletion.tooltip.mclogs_already_deleted"));
        }
        JButton deleteMetadataButton = createRowDeleteButton(
                "gui.log_deletion.delete_metadata", log, DeleteTarget.METADATA);
        deleteMetadataButton.setEnabled(log.canDeleteMetadata());
        if (!log.canDeleteMetadata()) {
            deleteMetadataButton.setToolTipText(LanguageProvider.get(log.wasMetadataDeleted()
                    ? "gui.log_deletion.tooltip.metadata_already_deleted"
                    : "gui.log_deletion.tooltip.metadata_never_uploaded"));
        }
        JButton deleteBothButton = createRowDeleteButton(
                "gui.log_deletion.delete_both", log, DeleteTarget.BOTH);
        deleteBothButton.setEnabled(log.canDeleteFromMcLogs() || log.canDeleteMetadata());

        buttons.add(openButton);
        buttons.add(deleteMcLogsButton);
        buttons.add(deleteMetadataButton);
        buttons.add(deleteBothButton);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private JButton createRowDeleteButton(String languageKey, UploadedLog log, DeleteTarget target) {
        JButton button = new JButton(LanguageProvider.get(languageKey));
        button.addActionListener(event -> delete(Collections.singletonList(log), target));
        return button;
    }

    private void open(UploadedLog log) {
        try {
            LinksHelper.browse(new URI(log.getUrl()));
        } catch (Exception exception) {
            CrashAssistantApp.LOGGER.error("Failed to open log URL", exception);
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String errorOrUnknown(String error) {
        return error == null || error.trim().isEmpty() ? "Unknown error" : error;
    }

    private static String metadataUnavailableTooltip(List<UploadedLog> logs) {
        boolean deleted = logs.stream().anyMatch(UploadedLog::wasMetadataDeleted);
        boolean neverUploaded = logs.stream().anyMatch(log ->
                !log.canDeleteMetadata() && !log.wasMetadataDeleted());
        if (deleted && neverUploaded) {
            return LanguageProvider.get("gui.log_deletion.tooltip.metadata_deleted_or_never_uploaded");
        }
        return LanguageProvider.get(deleted
                ? "gui.log_deletion.tooltip.metadata_already_deleted"
                : "gui.log_deletion.tooltip.metadata_never_uploaded");
    }

    private void showUnexpectedFailure(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        CrashAssistantApp.LOGGER.error("Unexpected error while deleting uploaded log data", cause);
        String message = LanguageProvider.get("gui.log_deletion.operation_failed")
                .replace("$ERROR$", errorOrUnknown(cause.getMessage()));
        JOptionPane.showMessageDialog(
                this,
                message,
                LanguageProvider.get("gui.log_deletion.operation_failed_title"),
                JOptionPane.ERROR_MESSAGE);
    }

    private static class DeleteOperationResult {
        private final BulkLogDeletionResponse mcLogsResponse;
        private final MetadataDeletionResponse metadataResponse;

        private DeleteOperationResult(
                BulkLogDeletionResponse mcLogsResponse,
                MetadataDeletionResponse metadataResponse
        ) {
            this.mcLogsResponse = mcLogsResponse;
            this.metadataResponse = metadataResponse;
        }
    }
}
