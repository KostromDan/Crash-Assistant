package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalyser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.app.utils.McLogsApiProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import gs.mclo.api.response.UploadLogResponse;
import gs.mclo.api.response.insights.Problem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FilePanel {
    private final JPanel panel;
    private final JButton showButton;
    private final JButton openButton;
    private final JButton uploadButton;
    private final JButton browserButton;
    private Exception lastError = null;
    private final Log log;

    public FilePanel(Log log) {
        this.log = log;

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        DragAndDrop.enableDragAndDrop(panel, Collections.singletonList(log.getFile()));

        JLabel fileNameLabel = new JLabel(log.getName());
        panel.add(fileNameLabel, BorderLayout.CENTER);

        JPanel spacerPanel = new JPanel();
        spacerPanel.setPreferredSize(new Dimension(0, 0));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        openButton = createButton(LanguageProvider.get("gui.open_button"), e -> openFile());
        showButton = createButton(LanguageProvider.get("gui.show_in_explorer_button"), e -> showInExplorer());

        uploadButton = createButton(LanguageProvider.get("gui.upload_and_copy_link_button"), e -> uploadFile());

        browserButton = createButton("\uD83C\uDF10", e -> openInBrowser());
        browserButton.setVisible(false);
        browserButton.setToolTipText(LanguageProvider.get("gui.browser_button_tooltip"));


        buttonPanel.add(spacerPanel);
        buttonPanel.add(openButton);
        buttonPanel.add(showButton);
        buttonPanel.add(uploadButton);
        buttonPanel.add(browserButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        panel.setMinimumSize(new Dimension(0, panel.getPreferredSize().height));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
    }

    public JButton createButton(String text, ActionListener actionListener) {
        JButton button = new JButton(text);
        button.addActionListener(actionListener);
        return button;
    }

    public JPanel getPanel() {
        return panel;
    }

    /**
     * Opens the file using the default application associated with its type.
     */
    private void openFile() {
        ControlPanel.stopMovingToTop = true;
        try {
            Desktop.getDesktop().open(log.getFile());
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to open file: ", e);
        }
    }


    /**
     * Opens the file's directory in the system file explorer and selects the file.
     */
    private void showInExplorer() {
        ControlPanel.stopMovingToTop = true;
        try {
            if (System.getProperty("os.name").startsWith("Windows")) {
                new ProcessBuilder("explorer.exe", "/select,", log.getPath().toAbsolutePath().toString()).start();
            } else {
                Desktop.getDesktop().open(log.getFile().getParentFile());
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to show file in explorer: ", e);
        }
    }

    private void openInBrowser() {
        String linkToCopy = log.getLinkToUploadedFirstLines();
        if (log.getLinkToUploadedLastLines() != null) {
            linkToCopy = showLogPartSelectionDialog(LanguageProvider.get("gui.split_log_dialog_action_browser"));
        }
        if (linkToCopy == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URL(linkToCopy).toURI());
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open in link browser: ", e);
        }

    }

    public Log getLog() {
        return log;
    }

    public Exception getLastError() {
        return lastError;
    }

    private void uploadFile() {
        uploadFile(true);
    }

    public synchronized void uploadFile(boolean fromButton) {
        ControlPanel.stopMovingToTop = true;
        if (!uploadButton.isEnabled()) {
            return;
        }
        uploadButton.setEnabled(false);
        new Thread(() -> {
            if (log.getLinkToUploadedFirstLines() == null) {
                lastError = null;
                uploadButton.setPreferredSize(new Dimension(uploadButton.getMinimumSize().width, 25));
                uploadButton.setText(LanguageProvider.get("gui.uploading"));

                try {
                    String oldText = uploadButton.getText();

                    if (!fromButton && log.getType() == LogType.CRASH_ASSISTANT) {
                        List<FilePanel> logsCodexSupports = CrashAssistantGUI.fileListPanel.filePanelList.stream()
                                .filter(x -> LogAnalyser.CodexSupportedLogTypes.contains(x.getLog().getType()))
                                .collect(Collectors.toList());
                        while (!logsCodexSupports.isEmpty()) {
                            uploadButton.setText(LanguageProvider.get("gui.delayed"));
                            Thread.sleep(100);
                            if (logsCodexSupports.stream().anyMatch(x -> x.getLastError() != null))
                                throw new UploadException("Crash Assistant log must be uploaded after logs, Codex supports. But encountered error while uploading one of them.");
                            if (logsCodexSupports.stream().allMatch(x -> x.getLog().getLinkToUploadedFirstLines() != null))
                                break;
                        }
                    }

                    uploadButton.setText(LanguageProvider.get("gui.preprocessing"));
                    log.getReader().readLogFile(true);
                    uploadButton.setText(oldText);
                    CompletableFuture<UploadLogResponse> completableResponseFirstLines = McLogsApiProvider.getMcLogsClient().uploadLog(log.getReader().getFirstLinesString());

                    String lastLines = log.getReader().getLastLinesString();
                    if (lastLines != null) {
                        CompletableFuture<UploadLogResponse> completableResponseLastLines = McLogsApiProvider.getMcLogsClient().uploadLog(lastLines);
                        UploadLogResponse responseLastLines = completableResponseLastLines.get();
                        responseLastLines.setClient(McLogsApiProvider.getMcLogsClient());
                        if (responseLastLines.isSuccess()) {
                            log.setLinkToUploadedLastLines(CrashAssistantGUI.transformLink(responseLastLines.getUrl()));
                        } else {
                            throw new UploadException("An error occurred when uploading file: " + responseLastLines.getError());
                        }
                    }
                    UploadLogResponse responseFirstLines = completableResponseFirstLines.get();
                    responseFirstLines.setClient(McLogsApiProvider.getMcLogsClient());


                    if (responseFirstLines.isSuccess()) {
                        String link = CrashAssistantGUI.transformLink(responseFirstLines.getUrl());
                        if (LogAnalyser.CodexSupportedLogTypes.contains(log.getType())) {
                            synchronized (KnownCrashReasonMessage.class) {
                                for (Problem problem : responseFirstLines.getInsights().get().getAnalysis().getProblems()) {
                                    KnownCrashReasonMessage.addCodexMessage(log, problem, link);
                                }
                                CrashAssistantGUI.showKnownCrashReasonsWarnings();
                            }
                        }
                        log.setLinkToUploadedFirstLines(link);
                    } else {
                        throw new UploadException("An error occurred when uploading file: " + responseFirstLines.getError());
                    }
                } catch (IOException | ExecutionException | InterruptedException | UploadException e) {
                    {
                        lastError = e;
                        CrashAssistantApp.LOGGER.info("Failed to upload file \"" + log.getPath() + "\": ", e);
                        uploadButton.setText(LanguageProvider.get("gui.error"));
                        CrashAssistantGUI.highlightButton(uploadButton, new Color(255, 100, 100), 2600);
                        if (fromButton) {
                            JOptionPane.showMessageDialog(
                                    panel,
                                    LanguageProvider.get("gui.failed_to_upload_file") + " \"" + log.getPath() + "\": " + e,
                                    LanguageProvider.get("gui.failed_to_upload_file") + "!",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                        new Timer().schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        uploadButton.setText(LanguageProvider.get("gui.upload_and_copy_link_button"));
                                        uploadButton.setEnabled(true);
                                    }
                                },
                                3000
                        );
                        return;
                    }
                }
            }
            String linkToCopy = log.getLinkToUploadedFirstLines();
            if (fromButton) {
                if (log.getLinkToUploadedLastLines() != null) {
                    linkToCopy = showLogPartSelectionDialog(LanguageProvider.get("gui.split_log_dialog_action_copy"));
                }
                if (linkToCopy != null) ClipboardUtils.copy(linkToCopy);

                transformCopyLinkButton();

                if (linkToCopy != null) {
                    uploadButton.setText(LanguageProvider.get("gui.copied"));
                    CrashAssistantGUI.highlightButton(uploadButton, new Color(100, 255, 100), 2600);
                    uploadButton.setEnabled(false);
                }
            }
            new Timer().schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            uploadButton.setText(LanguageProvider.get("gui.copy_link_button"));
                            transformCopyLinkButton();
                            uploadButton.setEnabled(true);
                        }
                    },
                    fromButton && linkToCopy != null ? 3000 : 0
            );
        }).start();
    }

    private void transformCopyLinkButton() {
        String oldText = uploadButton.getText();
        browserButton.setVisible(true);
        uploadButton.setText(LanguageProvider.get("gui.upload_and_copy_link_button"));
        uploadButton.setPreferredSize(new Dimension(uploadButton.getMinimumSize().width - browserButton.getMinimumSize().width - 5, uploadButton.getMinimumSize().height));
        uploadButton.setText(oldText);
    }

    public String getTooBigReasons(boolean forMsg) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        long size = log.getFile().length();
        List<String> tooBigReasons = new ArrayList<>();
        if (size > 10 * 1024 * 1024)
            tooBigReasons.add("~" + size / (1024 * 1024) + langFunc.apply("msg.mb"));
        if (log.getReader().getCountedLines() > 25000)
            tooBigReasons.add((log.getReader().isLineCountInterrupted() ? langFunc.apply("msg.over") + " " : "~") +
                    log.getReader().getCountedLines() / 1000 + langFunc.apply("msg.k_lines"));
        return tooBigReasons.isEmpty() ? "" : "(" + String.join(" & ", tooBigReasons) + ")";
    }

    public String getMessageWithBothLinks(boolean forMsg) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        return log.getParentName() + "[" + log.getFileName() + " " + langFunc.apply("gui.split_log_dialog_head").toLowerCase() + "](<" + log.getLinkToUploadedFirstLines() + ">) / " +
                "[" + langFunc.apply("gui.split_log_dialog_tail").toLowerCase() + "](<" + log.getLinkToUploadedLastLines() + ">) " + getTooBigReasons(forMsg);
    }

    public String showLogPartSelectionDialog(String action) {
        JEditorPane logSelectionPane = CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.copy_split_log_dialog_text").replace("$LOG_TOO_BIG_REASON$", getTooBigReasons(false)).replace("$FILE_NAME$", log.getFileName()).replace("$ACTION$", action), false);
        Object[] options = {
                LanguageProvider.get("gui.split_log_dialog_msg_with_both"),
                LanguageProvider.get("gui.split_log_dialog_head"),
                LanguageProvider.get("gui.split_log_dialog_tail")
        };
        JOptionPane optionPane;
        if (action.equals(LanguageProvider.get("gui.split_log_dialog_action_copy"))) {
            optionPane = new JOptionPane(
                    logSelectionPane,
                    JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    null,
                    options
            );
        } else {
            optionPane = new JOptionPane(
                    logSelectionPane,
                    JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.YES_NO_OPTION,
                    null,
                    Arrays.copyOfRange(options, 1, 3)
            );
        }
        synchronized (FileListPanel.class) {
            if (FileListPanel.currentLogSelectionDialog != null) return null;
            FileListPanel.currentLogSelectionDialog = optionPane.createDialog(
                    panel,
                    LanguageProvider.get("gui.copy_split_log_dialog_title")
            );
        }
        FileListPanel.currentLogSelectionDialog.setVisible(true);


        Object selectedValue;
        while ((selectedValue = optionPane.getValue()) == JOptionPane.UNINITIALIZED_VALUE) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        if (selectedValue == null) {

        } else if (selectedValue.equals(options[0])) {
            selectedValue = getMessageWithBothLinks(true);
        } else if (selectedValue.equals(options[1])) {
            selectedValue = log.getLinkToUploadedFirstLines();
        } else if (selectedValue.equals(options[2])) {
            selectedValue = log.getLinkToUploadedLastLines();
        }
        FileListPanel.currentLogSelectionDialog = null;
        return (String) selectedValue;
    }
}
