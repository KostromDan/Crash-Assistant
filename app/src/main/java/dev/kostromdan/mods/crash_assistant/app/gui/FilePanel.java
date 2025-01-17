package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.app.utils.LogProcessor;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import gs.mclo.api.response.UploadLogResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Timer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class FilePanel {
    private final JPanel panel;
    private final JButton showButton;
    private final JButton openButton;
    private final JButton uploadButton;
    private final JButton browserButton;
    private final Path filePath;
    private final String fileName;
    private String uploadedLinkFirstLines = null;
    private String uploadedLinkLastLines = null;
    private int countedLines;
    private boolean lineCountInterrupted = false;
    private Exception lastError = null;

    public FilePanel(String fileName, Path filePath) {
        this.filePath = filePath;

        this.fileName = fileName;

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        DragAndDrop.enableDragAndDrop(panel, Collections.singletonList(filePath.toFile()));

        JLabel fileNameLabel = new JLabel(fileName);
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
            Desktop.getDesktop().open(filePath.toFile());
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
                new ProcessBuilder("explorer.exe", "/select,", filePath.toAbsolutePath().toString()).start();
            } else {
                Desktop.getDesktop().open(filePath.toFile().getParentFile());
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to show file in explorer: ", e);
        }

    }

    private void openInBrowser() {
        String linkToCopy = uploadedLinkFirstLines;
        if (uploadedLinkLastLines != null) {
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

    public String getFileName() {
        return fileName;
    }

    public String getUploadedLinkFirstLines() {
        return uploadedLinkFirstLines;
    }

    public String getUploadedLinkLastLines() {
        return uploadedLinkLastLines;
    }

    public int getCountedLines() {
        return countedLines;
    }

    public boolean isLineCountInterrupted() {
        return lineCountInterrupted;
    }

    public Path getFilePath() {
        return filePath;
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
            if (uploadedLinkFirstLines == null) {
                lastError = null;
                uploadButton.setPreferredSize(new Dimension(uploadButton.getMinimumSize().width, 25));
                uploadButton.setText(LanguageProvider.get("gui.uploading"));

                try {
                    String oldText = uploadButton.getText();
                    uploadButton.setText(LanguageProvider.get("gui.preprocessing"));
                    LogProcessor logProcessor = new LogProcessor(filePath);
                    logProcessor.processLogFile();
                    uploadButton.setText(oldText);
                    CompletableFuture<UploadLogResponse> completableResponseFirstLines = CrashAssistantGUI.MCLogsClient.uploadLog(logProcessor.getFirstLinesString());
                    countedLines = logProcessor.getCountedLines();
                    lineCountInterrupted = logProcessor.isLineCountInterrupted();

                    String lastLines = logProcessor.getLastLinesString();
                    if (lastLines != null) {
                        CompletableFuture<UploadLogResponse> completableResponseLastLines = CrashAssistantGUI.MCLogsClient.uploadLog(lastLines);
                        UploadLogResponse responseLastLines = completableResponseLastLines.get();
                        responseLastLines.setClient(CrashAssistantGUI.MCLogsClient);
                        if (responseLastLines.isSuccess()) {
                            uploadedLinkLastLines = CrashAssistantGUI.transformLink(responseLastLines.getUrl());
                        } else {
                            throw new UploadException("An error occurred when uploading file: " + responseLastLines.getError());
                        }
                    }
                    UploadLogResponse responseFirstLines = completableResponseFirstLines.get();
                    responseFirstLines.setClient(CrashAssistantGUI.MCLogsClient);


                    if (responseFirstLines.isSuccess()) {
                        uploadedLinkFirstLines = CrashAssistantGUI.transformLink(responseFirstLines.getUrl());
                    } else {
                        throw new UploadException("An error occurred when uploading file: " + responseFirstLines.getError());
                    }
                } catch (IOException | ExecutionException | InterruptedException | UploadException e) {
                    {
                        lastError = e;
                        CrashAssistantApp.LOGGER.info("Failed to upload file \"" + filePath + "\": ", e);
                        uploadButton.setText(LanguageProvider.get("gui.error"));
                        CrashAssistantGUI.highlightButton(uploadButton, new Color(255, 100, 100), 2600);
                        if (fromButton) {
                            JOptionPane.showMessageDialog(
                                    panel,
                                    LanguageProvider.get("gui.failed_to_upload_file") + " \"" + filePath + "\": " + e,
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
            String linkToCopy = uploadedLinkFirstLines;
            if (fromButton) {
                if (uploadedLinkLastLines != null) {
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
        Function<String, String> langFunc = forMsg ? LanguageProvider::getMsgLang : LanguageProvider::get;
        long size = getFilePath().toFile().length();
        List<String> tooBigReasons = new ArrayList<>();
        if (size > 10 * 1024 * 1024)
            tooBigReasons.add("~" + size / (1024 * 1024) + langFunc.apply("msg.mb"));
        if (getCountedLines() > 25000)
            tooBigReasons.add((isLineCountInterrupted() ? langFunc.apply("msg.over") + " " : "~") +
                    getCountedLines() / 1000 + langFunc.apply("msg.k_lines"));
        return tooBigReasons.isEmpty() ? "" : "(" + String.join(" & ", tooBigReasons) + ")";
    }

    public String getMessageWithBothLinks(boolean forMsg) {
        Function<String, String> langFunc = forMsg ? LanguageProvider::getMsgLang : LanguageProvider::get;
        return getFileName() + ": " + "[" + langFunc.apply("gui.split_log_dialog_head").toLowerCase() + "](<" + getUploadedLinkFirstLines() + ">) / " +
                "[" + langFunc.apply("gui.split_log_dialog_tail").toLowerCase() + "](<" + getUploadedLinkLastLines() + ">) " + getTooBigReasons(forMsg) + "\n";
    }

    public String showLogPartSelectionDialog(String action) {
        JEditorPane logSelectionPane = CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.copy_split_log_dialog_text").replace("$LOG_TOO_BIG_REASON$", getTooBigReasons(false)).replace("$FILE_NAME$", fileName).replace("$ACTION$", action));
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
            selectedValue = getMessageWithBothLinks(false);
        } else if (selectedValue.equals(options[1])) {
            selectedValue = uploadedLinkFirstLines;
        } else if (selectedValue.equals(options[2])) {
            selectedValue = uploadedLinkLastLines;
        }
        FileListPanel.currentLogSelectionDialog = null;
        return (String) selectedValue;
    }
}
