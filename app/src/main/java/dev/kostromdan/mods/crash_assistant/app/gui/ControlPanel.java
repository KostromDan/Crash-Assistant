package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.TrustedDomainsHelper;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.platform.PlatformHelp;
import gs.mclo.api.response.UploadLogResponse;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
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
    private ModListDiff modListDiff;

    public ControlPanel(FileListPanel fileListPanel) {
        this.fileListPanel = fileListPanel;

        panel = new JPanel(new BorderLayout());

        JPanel labelButtonPanel = new JPanel();
        labelButtonPanel.setLayout(new BoxLayout(labelButtonPanel, BoxLayout.X_AXIS));

        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            modListDiff = ModListDiff.getDiff();
            String labelMsg;
            JButton showModListButton = new JButton(LanguageProvider.get("gui.show_modlist_diff_button"));
            if (modListDiff.isEmpty()) {
                labelMsg = LanguageProvider.get("gui.modlist_not_changed_label") + ":";
                showModListButton.setEnabled(false);
                showModListButton.setToolTipText(LanguageProvider.get("gui.modlist_not_changed_label"));
            } else {
                labelMsg = "<html><div style='white-space:nowrap;'>"
                        + LanguageProvider.get("gui.modlist_changed_label")
                        .replace("$ADDED_MODS_COUNT$", "<span style='color:green;'>" + modListDiff.getAddedMods().size() + "</span>")
                        .replace("$REMOVED_MODS_COUNT$", "<span style='color:red;'>" + modListDiff.getRemovedMods().size() + "</span>")
                        .replace("$UPDATED_MODS_COUNT$", "<span style='color:blue;'>" + modListDiff.getUpdatedMods().size() + "</span>")
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
        requestHelpButton.setToolTipText(PlatformHelp.getActualHelpLink());
        gbc.gridy = 1;
        bottomPanel.add(requestHelpButton, gbc);

        panel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public JPanel getPanel() {
        return panel;
    }

    public void requestHelp() {
        try {
            stopMovingToTop = true;
            String link = PlatformHelp.getActualHelpLink();
            URI uri = new URI(link);
            if (!TrustedDomainsHelper.isTrustedTopDomain(uri)) {
                String creatorWarning = "";
                if (CrashAssistantConfig.getModpackCreators().contains(ModListUtils.getCurrentUsername())) {
                    creatorWarning = "\n\n<b>The next text is seen only by modpack creators</b>:\n" +
                            "If you think your domain(" + TrustedDomainsHelper.getTopDomainName(uri) + ") should be in trusted domains,\n" +
                            "please contact us on <a href =https://github.com/KostromDan/Crash-Assistant/blob/1.19.2%2B/app/src/main/java/dev/kostromdan/mods/crash_assistant/app/utils/TrustedDomainsHelper.java>GitHub</a>.";
                }
                int result = JOptionPane.showConfirmDialog(
                        null,
                        CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.untrusted_domain_question") + "\n<a href =" + link + ">" + link + "</a>" + creatorWarning),
                        LanguageProvider.get("gui.untrusted_domain_title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }

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
        textPane.setText(modListDiff.generateDiffMsg(false).toHtml());
        textPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(Math.min(scrollPane.getPreferredSize().width + 15, 700), 300));

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
        new Thread(() -> {
            if (generatedMsg == null) {
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
                generatedMsg = CrashAssistantConfig.get("text.modpack_name", true) + " " + LanguageProvider.getMsgLang("msg.crashed").replace("$UPLOAD_TO$", CrashAssistantGUI.getUploadToLink()) + "\n";
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
                        String[] splitLog = panel.getFileName().split(":");
                        String fileName = (splitLog.length == 2 ? splitLog[1] : splitLog[0]).trim();
                        String fileParentName = splitLog.length == 2 ? splitLog[0] + ": " : "";
                        generatedMsg += fileParentName + "[" + fileName + "](<" + panel.getUploadedLinkFirstLines() + ">)\n";
                    } else {
                        generatedMsg += panel.getMessageWithBothLinks(true);

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
                String modlistDIff = modListDiff.generateDiffMsg(true).toText();
                int lineCount = modlistDIff.length() - modlistDIff.replace("\n", "").length();
                if (generatedMsg.length() + modlistDIff.length() >= 1650 || lineCount > 15 ||
                        (PlatformHelp.isLinkDefault() && PlatformHelp.platform == PlatformHelp.FORGE && lineCount >= 3)) {
                    try {
                        String link = uploadModlistDiff(modlistDIff);
                        generatedMsg += modlistDIff.split("\n", 2)[0] + "\n";
                        generatedMsg += LanguageProvider.getMsgLang("msg.big_size_diff_uploaded_0") + "[" + LanguageProvider.getMsgLang("msg.big_size_diff_uploaded_1") + "](<" + link + ">)" + LanguageProvider.getMsgLang("msg.big_size_diff_uploaded_2") + "\n";
                    } catch (ExecutionException | InterruptedException | UploadException e) {
                        CrashAssistantApp.LOGGER.error("Failed to upload modlist diff message", e);
                        generatedMsg += modlistDIff;
                    }
                } else {
                    generatedMsg += modlistDIff;
                }
            }

            String warningMsg = CrashAssistantConfig.get("generated_message.warning_after_upload_all_button_press", true);
            ClipboardUtils.copy(generatedMsg);
            int buttonHighLightTime = 3000;
            if (!uploadAllButtonWarningShown && !warningMsg.isEmpty()) {
                buttonHighLightTime = 4500;
                showUploadAllButtonWarning(warningMsg);
                ClipboardUtils.copy(generatedMsg);
            }
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
        }).start();
    }

    public static void showUploadAllButtonWarning(String warningMsg) {
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
        dialog.setVisible(true);
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
