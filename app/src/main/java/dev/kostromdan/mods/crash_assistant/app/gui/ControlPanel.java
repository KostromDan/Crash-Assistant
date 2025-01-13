package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;
import gs.mclo.api.response.UploadLogResponse;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ControlPanel {
    public static boolean stopMovingToTop = false;
    private static boolean moddedMinecraftWarningShown = false;
    private static JPanel panel;
    public static JDialog dialog;
    private final FileListPanel fileListPanel;
    public final JButton uploadAllButton;
    public final JButton requestHelpButton;
    private String generatedMsg = null;

    public ControlPanel(FileListPanel fileListPanel) {
        this.fileListPanel = fileListPanel;

        panel = new JPanel(new BorderLayout());

        JPanel labelButtonPanel = new JPanel();
        labelButtonPanel.setLayout(new BoxLayout(labelButtonPanel, BoxLayout.X_AXIS));

        if (CrashAssistantConfig.get("modpack_modlist.enabled")) {
            ModListDiff diff = ModListUtils.getDiff();
            String labelMsg;
            JButton showModListButton = new JButton(LanguageProvider.get("gui.show_modlist_diff_button"));
            if (diff.addedMods().isEmpty() && diff.removedMods().isEmpty()) {
                labelMsg = LanguageProvider.get("gui.modlist_not_changed_label") + ":";
                showModListButton.setEnabled(false);
                showModListButton.setToolTipText(LanguageProvider.get("gui.modlist_not_changed_label"));
            } else {
                labelMsg = "<html><div style='white-space:nowrap;'>"
                        + LanguageProvider.get("gui.modlist_changed_label")
                        .replace("$ADDED_MODS_COUNT$", "<span style='color:green;'>" + diff.addedMods().size() + "</span>")
                        .replace("$REMOVED_MODS_COUNT$", "<span style='color:red;'>" + diff.removedMods().size() + "</span>")
                        + "</div></html>";
            }

            JLabel label = new JLabel(labelMsg);
            label.setMaximumSize(label.getPreferredSize());
            showModListButton.addActionListener(e -> showModList());

            showModListButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, showModListButton.getPreferredSize().height));

            labelButtonPanel.add(label);
            labelButtonPanel.add(Box.createHorizontalStrut(10));
            labelButtonPanel.add(showModListButton);
            labelButtonPanel.add(Box.createHorizontalGlue());

            panel.add(labelButtonPanel, BorderLayout.NORTH);
        }

        JPanel bottomPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        uploadAllButton = new JButton(LanguageProvider.get("gui.upload_all_button"));
        uploadAllButton.addActionListener(e -> uploadAllFiles());
        gbc.gridy = 0;
        bottomPanel.add(uploadAllButton, gbc);

        requestHelpButton = new JButton(LanguageProvider.get("gui.request_help_button"));
        requestHelpButton.addActionListener(e -> requestHelp());
        gbc.gridy = 1;
        bottomPanel.add(requestHelpButton, gbc);

        panel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public JPanel getPanel() {
        return panel;
    }

    public void requestHelp() {
        stopMovingToTop = true;
        try {
            URI uri = new URL(CrashAssistantConfig.get("general.help_link").toString()).toURI();
            Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open help_link in browser: ", e);
        }
    }

    private void showModList() {
        stopMovingToTop = true;
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setContentType("text/html");
        textPane.setText(ModListUtils.generateDiffMsg(true));
        textPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(Math.min(scrollPane.getPreferredSize().width, 700), 300));

        JOptionPane.showMessageDialog(
                null,
                scrollPane,
                LanguageProvider.get("gui.modlist_diff_dialog_name"),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void uploadAllFiles() {
        stopMovingToTop = true;
        if (!moddedMinecraftWarningShown && Objects.equals(CrashAssistantConfig.get("general.help_link"), "https://discord.gg/moddedmc")) {
            JEditorPane commentPane = CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.upload_all_button_moddedmc_warning", new HashSet<>() {{
                add("$LANG.gui.file_list_label$");
            }}));
            JOptionPane optionPane = new JOptionPane(
                    commentPane,
                    JOptionPane.WARNING_MESSAGE,
                    JOptionPane.DEFAULT_OPTION
            );
            dialog = optionPane.createDialog(
                    panel,
                    LanguageProvider.get("gui.upload_all_button_moddedmc_warning_title")
            );
            moddedMinecraftWarningShown = true;
            dialog.setVisible(true);
            return;
        }
        new Thread(() -> {
            uploadAllButton.setEnabled(false);
            if (generatedMsg == null) {
                uploadAllButton.setText(LanguageProvider.get("gui.uploading"));
                for (FilePanel panel : fileListPanel.filePanelList) {
                    panel.uploadFile(false);
                }
                outerLoop:
                while (true) {
                    int successCounter = 0;
                    for (FilePanel filePanel : fileListPanel.filePanelList) {
                        if (filePanel.getLastError() != null) {
                            JOptionPane.showMessageDialog(
                                    panel,
                                    "Failed to upload file \"" + filePanel.getFilePath() + "\": " + filePanel.getLastError(),
                                    "Failed to upload file!",
                                    JOptionPane.ERROR_MESSAGE
                            );
                            uploadAllButton.setText(LanguageProvider.get("gui.error"));
                            new java.util.Timer().schedule(
                                    new java.util.TimerTask() {
                                        @Override
                                        public void run() {
                                            uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
                                            uploadAllButton.setEnabled(true);
                                        }
                                    },
                                    3000
                            );
                            return;
                        }
                        if (filePanel.getUploadedLinkFirstLines() != null) {
                            successCounter++;
                        }
                        if (successCounter == fileListPanel.filePanelList.size()) {
                            break outerLoop;
                        }
                        try {
                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }
                generatedMsg = LanguageProvider.get("text.modpack_name") + " crashed!\n";
                for (FilePanel panel : fileListPanel.filePanelList) {
                    if (panel.getUploadedLinkLastLines() == null) {
                        generatedMsg += panel.getFileName() + ": [link](<" + panel.getUploadedLinkFirstLines() + ">)\n";
                    } else {
                        long size = panel.getFilePath().toFile().length();
                        generatedMsg += panel.getFileName() + ": ";
                        generatedMsg += "[head](<" + panel.getUploadedLinkFirstLines() + ">) / ";
                        generatedMsg += "[tail](<" + panel.getUploadedLinkLastLines() + ">) (" + ((size > 10 * 1024 * 1024) ? "over " + size / (1024 * 1024) + "MB" : "over 25k lines") + ")\n";

                    }
                }
                generatedMsg += "\n";
                String modlistDIff = ModListUtils.generateDiffMsg();
                if (generatedMsg.length() + modlistDIff.length() >= 2000) {
                    try {
                        String link = uploadModlistDiff(modlistDIff);
                        generatedMsg += modlistDIff.split("\n", 2)[0] + "\n";
                        generatedMsg += "Due to big size of mod list diff, it was uploaded to: [link](<" + link + ">)";
                    } catch (ExecutionException | InterruptedException | UploadException e) {
                        CrashAssistantApp.LOGGER.error("Failed to upload modlist diff message", e);
                        generatedMsg += modlistDIff;
                    }
                } else {
                    generatedMsg += modlistDIff;
                }
            }
            ClipboardUtils.copy(generatedMsg);
            uploadAllButton.setText(LanguageProvider.get("gui.copied"));
            CrashAssistantGUI.highlightButton(uploadAllButton, new Color(100, 255, 100), 2600);
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            uploadAllButton.setText(LanguageProvider.get("gui.upload_all_finished_button"));
                            uploadAllButton.setEnabled(true);
                        }
                    },
                    3000
            );
        }).start();
    }

    public String uploadModlistDiff(String diff) throws ExecutionException, InterruptedException, UploadException {
        UploadLogResponse response = CrashAssistantGUI.MCLogsClient.uploadLog(diff).get();
        response.setClient(CrashAssistantGUI.MCLogsClient);

        if (response.isSuccess()) {
            return response.getUrl();
        } else {
            throw new UploadException("An error occurred when uploading modlist diff: " + response.getError());
        }

    }
}
