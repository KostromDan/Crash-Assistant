package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import gs.mclo.api.response.UploadLogResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class FilePanel {
    private final JPanel panel;
    private final JButton showButton;
    private final JButton openButton;
    private final JButton uploadButton;
    private final JButton browserButton;
    private final Path filePath;
    private final String fileName;
    private String uploadedLink = null;
    private Exception lastError = null;

    public FilePanel(String fileName, Path filePath) {
        this.filePath = filePath;

        this.fileName = fileName;

        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

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
        try {
            Desktop.getDesktop().browse(new URL(uploadedLink).toURI());
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open in link browser: ", e);
        }

    }

    public String getFileName() {
        return fileName;
    }

    public String getUploadedLink() {
        return uploadedLink;
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

    public void uploadFile(boolean fromButton) {
        new Thread(() -> {
            if (uploadedLink == null) {
                lastError = null;
                uploadButton.setEnabled(false);
                uploadButton.setPreferredSize(new Dimension(uploadButton.getMinimumSize().width, 25));
                uploadButton.setText(LanguageProvider.get("gui.uploading"));

                try {
                    UploadLogResponse response = CrashAssistantGUI.MCLogsClient.uploadLog(filePath).get();
                    response.setClient(CrashAssistantGUI.MCLogsClient);

                    if (response.isSuccess()) {
                        uploadedLink = response.getUrl();
                    } else {
                        throw new UploadException("An error occurred when uploading file: " + response.getError());
                    }
                } catch (IOException | ExecutionException | InterruptedException | UploadException e) {
                    {
                        lastError = e;
                        CrashAssistantApp.LOGGER.info("Failed to upload file \"" + filePath + "\": ", e);
                        uploadButton.setText(LanguageProvider.get("gui.error"));
                        if (fromButton) {
                            JOptionPane.showMessageDialog(
                                    panel,
                                    LanguageProvider.get("gui.failed_to_upload_file") + " \"" + filePath + "\": " + e,
                                    LanguageProvider.get("gui.failed_to_upload_file") + "!",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                        new java.util.Timer().schedule(
                                new java.util.TimerTask() {
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
            if (fromButton) {
                ClipboardUtils.copy(uploadedLink);

                transformCopyLinkButton();

                uploadButton.setText(LanguageProvider.get("gui.copied"));
                CrashAssistantGUI.highlightButton(uploadButton, new Color(100, 255, 100), 2600);
                uploadButton.setEnabled(false);
            }
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            uploadButton.setText(LanguageProvider.get("gui.copy_link_button"));
                            transformCopyLinkButton();
                            uploadButton.setEnabled(true);
                        }
                    },
                    fromButton ? 3000 : 0
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
}
