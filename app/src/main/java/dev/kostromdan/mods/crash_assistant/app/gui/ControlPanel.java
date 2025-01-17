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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ControlPanel {
    public static boolean stopMovingToTop = false;
    private static boolean uploadAllButtonWarningShown = false;
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

        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
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
        uploadAllButton.setEnabled(false);
        String warningMsg = CrashAssistantConfig.get("generated_message.warning_after_upload_all_button_press", true);
        if (!uploadAllButtonWarningShown && !warningMsg.isEmpty()) {
            new Thread(() -> {
                JEditorPane commentPane = CrashAssistantGUI.getEditorPane(warningMsg);
                JOptionPane optionPane = new JOptionPane(
                        commentPane,
                        JOptionPane.INFORMATION_MESSAGE,
                        JOptionPane.DEFAULT_OPTION
                );
                dialog = optionPane.createDialog(
                        panel,
                        LanguageProvider.get("gui.upload_all_button_warning_title")
                );
                uploadAllButtonWarningShown = true;
                synchronized (ControlPanel.class) {
                    dialog.setVisible(true);
                }
            }).start();

        }
        new Thread(() -> {
            final boolean generatedMsgWasNull = generatedMsg == null;
            if (generatedMsgWasNull) {
                uploadAllButton.setText(LanguageProvider.get("gui.uploading"));
                for (FilePanel panel : fileListPanel.filePanelList) {
                    panel.uploadFile(false);
                }
                outerLoop:
                while (true) {
                    if (fileListPanel.filePanelList.isEmpty()) {
                        break;
                    }
                    int successCounter = 0;
                    for (FilePanel filePanel : fileListPanel.filePanelList) {
                        if (filePanel.getLastError() != null) {
                            JOptionPane.showMessageDialog(
                                    panel,
                                    LanguageProvider.get("gui.failed_to_upload_file") + " \"" + filePanel.getFilePath() + "\": " + filePanel.getLastError(),
                                    LanguageProvider.get("gui.failed_to_upload_file") + "!",
                                    JOptionPane.ERROR_MESSAGE
                            );
                            uploadAllButton.setText(LanguageProvider.get("gui.error"));
                            CrashAssistantGUI.highlightButton(uploadAllButton, new Color(255, 100, 100), 2600);

                            new Timer().schedule(
                                    new TimerTask() {
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
                generatedMsg = CrashAssistantConfig.get("text.modpack_name", true) + " " + LanguageProvider.getMsgLang("msg.crashed") + "!\n";
                if (!CrashAssistantConfig.get("generated_message.text_under_crashed").toString().isEmpty()) {
                    generatedMsg += CrashAssistantConfig.get("generated_message.text_under_crashed", true) + "\n";
                }
                boolean kubeJSPosted = false;
                List<FilePanel> kubeJSPanelList = new ArrayList<>();
                for (FilePanel panel : fileListPanel.filePanelList) {
                    if (!panel.getFileName().startsWith("KubeJS: ")) {
                        continue;
                    }
                    if (panel.getUploadedLinkLastLines() != null) {
                        kubeJSPanelList.clear();
                        break;
                    }
                    kubeJSPanelList.add(panel);
                }
                boolean containsTooBigLog = false;
                for (FilePanel panel : fileListPanel.filePanelList) {
                    if (panel.getFileName().startsWith("KubeJS: ")) {
                        if (kubeJSPosted) continue;

                        if (!kubeJSPanelList.isEmpty()) {
                            kubeJSPosted = true;
                            generatedMsg += "KubeJS: ";
                            generatedMsg += kubeJSPanelList.stream()
                                    .map(kubeJSPanel -> "[" + kubeJSPanel.getFilePath().getFileName() + "](<" + kubeJSPanel.getUploadedLinkFirstLines() + ">)")
                                    .collect(Collectors.joining(" / "));
                            generatedMsg += "\n";
                            continue;
                        }
                    }
                    if (panel.getUploadedLinkLastLines() == null) {
                        generatedMsg += panel.getFileName() + ": [" + CrashAssistantGUI.getUploadToLink() + "](<" + panel.getUploadedLinkFirstLines() + ">)\n";
                    } else {
                        containsTooBigLog = true;
                        generatedMsg += panel.getMessageWithBothLinks();

                    }
                }
                if (CrashAssistantApp.launcherLogsCount == 0) {
                    try {
                        Path curseForgeDir = Paths.get("").toAbsolutePath().getParent().getParent();
                        List<String> curseForgeDirContents = Files.list(curseForgeDir).map(dirPath -> dirPath.getFileName().toString().toLowerCase()).toList();
                        if (curseForgeDirContents.contains("instances") && curseForgeDirContents.contains("install")) {
                            generatedMsg += LanguageProvider.getMsgLang("msg.skip_launcher") + "\n";
                        }
                    } catch (Exception ignored) {
                    }
                }
                generatedMsg += "\n";
                String modlistDIff = ModListUtils.generateDiffMsg();
                String containsTooBigLogMsg = containsTooBigLog && CrashAssistantConfig.getBoolean("generated_message.generated_msg_includes_info_why_split") && !CrashAssistantGUI.isLinkToModdedMC() ?
                        "\n*" + LanguageProvider.getMsgLang("msg.log_was_split") + "*" : "";
                if (generatedMsg.length() + modlistDIff.length() + containsTooBigLogMsg.length() >= 2000) {
                    try {
                        String link = uploadModlistDiff(modlistDIff);
                        generatedMsg += modlistDIff.split("\n", 2)[0] + "\n";
                        generatedMsg += LanguageProvider.getMsgLang("msg.big_size_diff_uploaded") + ": [" + CrashAssistantGUI.getUploadToLink() + "](<" + link + ">)\n";
                    } catch (ExecutionException | InterruptedException | UploadException e) {
                        CrashAssistantApp.LOGGER.error("Failed to upload modlist diff message", e);
                        generatedMsg += modlistDIff;
                    }
                } else {
                    generatedMsg += modlistDIff;
                }
                if (containsTooBigLog) {
                    generatedMsg += containsTooBigLogMsg;
                }
            }
            synchronized (ControlPanel.class) {
                int buttonHighLightTime = 3000;
                if (generatedMsgWasNull && !warningMsg.isEmpty()) {
                    buttonHighLightTime = 4500;
                }
                ClipboardUtils.copy(generatedMsg);
                uploadAllButton.setText(LanguageProvider.get("gui.copied"));
                CrashAssistantGUI.highlightButton(uploadAllButton, new Color(100, 255, 100), buttonHighLightTime - 400);
                new Timer().schedule(
                        new TimerTask() {
                            @Override
                            public void run() {
                                uploadAllButton.setText(LanguageProvider.get("gui.upload_all_finished_button"));
                                uploadAllButton.setEnabled(true);
                            }
                        },
                        buttonHighLightTime
                );
            }
        }).start();
    }

    public String uploadModlistDiff(String diff) throws ExecutionException, InterruptedException, UploadException {
        UploadLogResponse response = CrashAssistantGUI.MCLogsClient.uploadLog(diff).get();
        response.setClient(CrashAssistantGUI.MCLogsClient);

        if (response.isSuccess()) {
            return CrashAssistantGUI.transformLink(response.getUrl());
        } else {
            throw new UploadException("An error occurred when uploading modlist diff: " + response.getError());
        }

    }
}
