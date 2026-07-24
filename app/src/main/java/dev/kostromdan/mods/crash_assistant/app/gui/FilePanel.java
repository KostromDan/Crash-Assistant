package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.DeclinedException;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalyser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.app.utils.LinksHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.MclogArrayRegistrar;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.ApiProvider;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.Problem;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.UploadLogResponse;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FilePanel {
    private final JPanel panel;
    private final JButton showButton;
    private final JButton openButton;
    private final JButton uploadButton;
    private final JButton browserButton;
    private Exception lastError = null;
    private boolean waiting = true;
    public static final Object uploadErrorDialogLock = new Object();
    private final Log log;
    private final int fullButtonWidth;

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

        browserButton = createButtonWithIcon("assets/internet.png", e -> openInBrowser());
        browserButton.setVisible(false);
        browserButton.setToolTipText(LanguageProvider.get("gui.browser_button_tooltip"));

        uploadButton = createButton(LanguageProvider.get("gui.upload_and_copy_link_button"), e -> uploadFile());
        synchronized (ControlPanel.class) {
            uploadButton.setEnabled(ControlPanel.uploadButtonsActivated);
        }

        fullButtonWidth = calculateMaxButtonWidth();
        Dimension dim = new Dimension(fullButtonWidth, uploadButton.getPreferredSize().height);

        // Ensure browser button has same height as upload button to prevent resizing
        Dimension browserDim = new Dimension(browserButton.getPreferredSize().width, dim.height);
        browserButton.setPreferredSize(browserDim);
        browserButton.setMinimumSize(browserDim);

        uploadButton.setPreferredSize(dim);
        uploadButton.setMinimumSize(dim);


        buttonPanel.add(spacerPanel);
        buttonPanel.add(openButton);
        buttonPanel.add(showButton);
        buttonPanel.add(uploadButton);
        buttonPanel.add(browserButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        panel.setMinimumSize(new Dimension(0, panel.getPreferredSize().height));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
    }

    private int calculateMaxButtonWidth() {
        List<String> singleStateTexts = Arrays.asList(
                LanguageProvider.get("gui.upload_and_copy_link_button"),
                LanguageProvider.get("gui.uploading"),
                LanguageProvider.get("gui.delayed"),
                LanguageProvider.get("gui.preprocessing"),
                LanguageProvider.get("gui.error")
        );

        List<String> copyStateTexts = Arrays.asList(
                LanguageProvider.get("gui.copied"),
                LanguageProvider.get("gui.copy_link_button")
        );

        int max = 0;
        JButton dummy = new JButton();
        dummy.setBorder(uploadButton.getBorder());
        dummy.setMargin(uploadButton.getMargin());
        dummy.setFont(uploadButton.getFont());

        // Measure texts that appear alone (without browser button)
        for (String s : singleStateTexts) {
            if (s != null) {
                dummy.setText(s);
                max = Math.max(max, dummy.getPreferredSize().width);
            }
        }

        // Measure texts that appear with the browser button
        // Total width = Button Width + Gap (5) + Browser Button Width
        int browserButtonWidth = browserButton.getPreferredSize().width;
        for (String s : copyStateTexts) {
            if (s != null) {
                dummy.setText(s);
                int totalRequired = dummy.getPreferredSize().width + 5 + browserButtonWidth;
                max = Math.max(max, totalRequired);
            }
        }

        // Add small safety padding
        return max + 4;
    }

    public JButton createButton(String text, ActionListener actionListener) {
        JButton button = new JButton(text);
        button.addActionListener(actionListener);
        return button;
    }

    private JButton createButtonWithIcon(String iconPath, ActionListener actionListener) {
        JButton button = new JButton();
        try {
            InputStream imageStream = FilePanel.class.getClassLoader().getResourceAsStream(iconPath);
            if (imageStream != null) {
                BufferedImage originalImage = ImageIO.read(imageStream);

                JButton temp = new JButton("\uD83C\uDF10");
                FontMetrics fm = temp.getFontMetrics(temp.getFont());
                int iconHeight = fm.getHeight();

                Image resized = originalImage.getScaledInstance(iconHeight, iconHeight, Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(resized);
                button.setIcon(icon);
            } else {
                button.setText("\uD83C\uDF10");
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error creating button with icon: ", e);
            button.setText("\uD83C\uDF10");
        }

        Dimension textSize = new JButton("\uD83C\uDF10").getPreferredSize();
        button.setPreferredSize(textSize);
        button.setMinimumSize(button.getPreferredSize());

        button.setMargin(new Insets(0, 0, 0, 0));

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
            LinksHelper.browse(new URL(linkToCopy).toURI());
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

    public boolean isUploadButtonEnabled() {
        return uploadButton.isEnabled();
    }

    public void setUploadButtonEnabled(boolean enabled) {
        uploadButton.setEnabled(enabled);
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

        lastError = null;
        new Thread(() -> {
            if(!fromButton && log.getType() == LogType.CRASH_ASSISTANT && log.getLinkToUploadedFirstLines() != null){
                untransformCopyLinkButton();
                log.setLinkToUploadedFirstLines(null);
                log.setLinkToUploadedLastLines(null);
            }
            if (log.getLinkToUploadedFirstLines() == null) {
                uploadButton.setText(LanguageProvider.get("gui.uploading"));

                try {
                    if (!PrivacyPolicyDialog.ensurePrivacyPolicyAccepted()) {
                        throw new DeclinedException(LanguageProvider.get("gui.privacy.declined"));
                    }

                    String oldText = uploadButton.getText();

                    if (!fromButton && log.getType() == LogType.CRASH_ASSISTANT) {
                        List<FilePanel> allLogsList = CrashAssistantGUI.fileListPanel.getFilePanelList().stream()
                                .filter(x -> x.getLog().getType() != LogType.CRASH_ASSISTANT)
                                .collect(Collectors.toList());
                        while (!allLogsList.isEmpty()) {
                            uploadButton.setText(LanguageProvider.get("gui.delayed"));
                            Thread.sleep(100);
                            if (allLogsList.stream().anyMatch(x -> x.getLastError() != null))
                                throw new UploadException("Crash Assistant log must be uploaded after all another logs. But encountered error while uploading one of them.");
                            if (allLogsList.stream().allMatch(x -> x.getLog().getLinkToUploadedFirstLines() != null))
                                break;
                        }
                    }

                    uploadButton.setText(LanguageProvider.get("gui.preprocessing"));
                    log.getReader().readLogFile(true);
                    uploadButton.setText(oldText);
                    CompletableFuture<UploadLogResponse> completableResponseFirstLines = ApiProvider.getMcLogsClient().uploadLog(log.getName(), log.getReader().getFirstLinesString());

                    String lastLines = log.getReader().getLastLinesString();
                    if (lastLines != null) {
                        CompletableFuture<UploadLogResponse> completableResponseLastLines = ApiProvider.getMcLogsClient().uploadLog(log.getName() + " (Last Lines)", lastLines);
                        UploadLogResponse responseLastLines = completableResponseLastLines.get();
                        if (responseLastLines.isSuccess()) {
                            String finalLink = CrashAssistantGUI.transformLink(responseLastLines.getUrl());
                            CrashAssistantApp.LOGGER.info("{} last lines uploaded successfully: {}", log.getName(), finalLink);
                            log.setLinkToUploadedLastLines(finalLink);
                            MclogArrayRegistrar.registerUploadedLog(
                                    log,
                                    responseLastLines.getId(),
                                    responseLastLines.getCreated(),
                                    log.getFileName() + " tail",
                                    MclogArrayRegistrar.priorityFor(log, 1));
                        } else {
                            if (responseLastLines.isNetworkError()) {
                                throw UploadException.network("An error occurred when uploading file: " + responseLastLines.getError());
                            }
                            throw new UploadException("An error occurred when uploading file: " + responseLastLines.getError());
                        }
                    }
                    UploadLogResponse responseFirstLines = completableResponseFirstLines.get();


                    if (responseFirstLines.isSuccess()) {
                        String finalLink = CrashAssistantGUI.transformLink(responseFirstLines.getUrl());
                        CrashAssistantApp.LOGGER.info("{} " + (lastLines != null ? "first lines " : "") + "uploaded successfully: {}", log.getName(), finalLink);
                        if (LogAnalyser.CodexSupportedLogTypes.contains(log.getType())) {
                            synchronized (KnownCrashReasonMessage.class) {
                                for (Problem problem : responseFirstLines.getInsights().getProblems()) {
                                    KnownCrashReasonMessage.addCodexMessage(log, problem, finalLink);
                                }
                                CrashAssistantGUI.showKnownCrashReasonsWarnings();
                            }
                        }
                        log.setLinkToUploadedFirstLines(finalLink);
                        MclogArrayRegistrar.registerUploadedLog(
                                log,
                                responseFirstLines.getId(),
                                responseFirstLines.getCreated(),
                                lastLines != null ? log.getFileName() + " head" : log.getFileName(),
                                MclogArrayRegistrar.priorityFor(log, 0));
                    } else {
                        if (responseFirstLines.isNetworkError()) {
                            throw UploadException.network("An error occurred when uploading file: " + responseFirstLines.getError());
                        }
                        throw new UploadException("An error occurred when uploading file: " + responseFirstLines.getError());
                    }
                } catch (Exception e) {
                    {
                        lastError = e;
                        CrashAssistantApp.LOGGER.info("Failed to upload file \"" + log.getPath() + "\": ", e);
                        uploadButton.setText(LanguageProvider.get("gui.error"));
                        CrashAssistantGUI.highlightButton(uploadButton, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_error_color"), new Color(255, 100, 100)), 2800);
                        if (fromButton && !(e instanceof DeclinedException)) {
                            synchronized (uploadErrorDialogLock) {
                                if (UploadErrorDialog.isNetworkUploadError(e)) {
                                    UploadErrorDialog.show(panel, log, UploadErrorDialog.formatErrorMessage(e));
                                } else {
                                    String message = LanguageProvider.get("gui.failed_to_upload_file") + " \"" + log.getPath() + "\": " + e;
                                    JOptionPane.showMessageDialog(
                                            panel,
                                            message,
                                            LanguageProvider.get("gui.failed_to_upload_file") + "!",
                                            JOptionPane.ERROR_MESSAGE
                                    );
                                }
                            }
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
            String toCopy = null;
            if (fromButton) {
                if (log.getLinkToUploadedLastLines() != null) {
                    boolean skipSplitDialog = CrashAssistantConfig.getBoolean("copied_links.skip_split_dialog");
                    if (skipSplitDialog) {
                        toCopy = getSplitLogCopyMessageWithBothLinks();
                    } else {
                        AtomicReference<String> result = new AtomicReference<>();
                        try {
                            SwingUtilities.invokeAndWait(() -> {
                                result.set(showLogPartSelectionDialog(LanguageProvider.get("gui.split_log_dialog_action_copy")));
                            });
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        toCopy = result.get();
                    }
                } else if (log.getLinkToUploadedFirstLines() != null) {
                    toCopy = CrashAssistantConfig.get("copied_links.single_link", true);
                    toCopy = toCopy.replace("$LINK$", log.getLinkToUploadedFirstLines());
                }
                if (toCopy != null) {
                    toCopy = toCopy.replace("$FILE_NAME$", log.getFileName());
                    toCopy = toCopy.replace("$LOG_NAME$", log.getParentName());
                    toCopy = toCopy.replace("$TOO_BIG_REASONS$", getTooBigReasons(true));
                    ClipboardUtils.copy(toCopy);
                }

                transformCopyLinkButton();

                if (toCopy != null) {
                    uploadButton.setText(LanguageProvider.get("gui.copied"));
                    CrashAssistantGUI.highlightButton(uploadButton, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_success_color"), new Color(100, 255, 100)), 2800);
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
                    fromButton && log.getLinkToUploadedFirstLines() != null ? 3000 : 0
            );
        }).start();
    }

    private void transformCopyLinkButton() {
        String oldText = uploadButton.getText();
        browserButton.setVisible(true);
        int newWidth = fullButtonWidth - browserButton.getPreferredSize().width - 5;
        uploadButton.setPreferredSize(new Dimension(newWidth, uploadButton.getPreferredSize().height));
        uploadButton.setText(oldText);
    }

    private void untransformCopyLinkButton() {
        browserButton.setVisible(false);
    }

    public String getTooBigReasons(boolean forMsg) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        long size = log.getFile().length();
        List<String> tooBigReasons = new ArrayList<>();
        if (size > 10 * 1024 * 1024)
            tooBigReasons.add("~" + size / (1024 * 1024) + langFunc.apply("msg.mb"));
        if (log.getReader().getCountedLines() > 25000) {
            boolean lineCountInterrupted = log.getReader().isLineCountInterrupted();
            tooBigReasons.add((lineCountInterrupted ? langFunc.apply("msg.over") + " " : "~") +
                    log.getReader().getCountedLines() / 1000 + langFunc.apply("msg.k_lines") +
                    (lineCountInterrupted ? langFunc.apply("msg.over_end") : ""));
        }
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
        boolean forCopy = action.equals(LanguageProvider.get("gui.split_log_dialog_action_copy"));
        if (forCopy) {
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
            selectedValue = getSplitLogCopyMessageWithBothLinks();
        } else if (selectedValue.equals(options[1])) {
            if (!forCopy) {
                selectedValue = log.getLinkToUploadedFirstLines();
            } else {
                String toCopy = CrashAssistantConfig.get("copied_links.single_link_split", true);
                toCopy = toCopy.replace("$LINK$", this.log.getLinkToUploadedFirstLines());
                toCopy = toCopy.replace("$HEAD_OR_TAIL$", LanguageProvider.getMsgLang("gui.split_log_dialog_head").toLowerCase(Locale.ROOT));
                selectedValue = toCopy;
            }
        } else if (selectedValue.equals(options[2])) {
            if (!forCopy) {
                selectedValue = log.getLinkToUploadedLastLines();
            } else {
                String toCopy = CrashAssistantConfig.get("copied_links.single_link_split", true);
                toCopy = toCopy.replace("$LINK$", this.log.getLinkToUploadedLastLines());
                toCopy = toCopy.replace("$HEAD_OR_TAIL$", LanguageProvider.getMsgLang("gui.split_log_dialog_tail").toLowerCase(Locale.ROOT));
                selectedValue = toCopy;
            }
        }
        FileListPanel.currentLogSelectionDialog = null;
        return (String) selectedValue;
    }

    private String getSplitLogCopyMessageWithBothLinks() {
        String toCopy = CrashAssistantConfig.get("copied_links.both_links_split", true);
        toCopy = toCopy.replace("$LINK_FIRST_LINES$", this.log.getLinkToUploadedFirstLines());
        toCopy = toCopy.replace("$LINK_LAST_LINES$", this.log.getLinkToUploadedLastLines());
        return toCopy;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }
}
