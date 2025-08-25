package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.DeclinedException;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.OutOfMemoryError;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParsingResult;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.FileUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.IntelCorruptedProcessorChecker;
import dev.kostromdan.mods.crash_assistant.app.utils.TrustedDomainsHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.ApiProvider;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.UploadLogResponse;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.*;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ControlPanel {
    public static boolean stopMovingToTop = false;
    public static boolean uploadButtonsActivated = false;
    private static boolean uploadAllButtonWarningShown = false;
    private static JPanel panel;
    public static JDialog dialog;
    private final FileListPanel fileListPanel;
    public final JButton uploadAllButton;
    public final JButton requestHelpButton;
    private String generatedMsg = null;
    private JLabel modListLabel;
    private JButton showModListButton;

    public ControlPanel(FileListPanel fileListPanel) {
        this.fileListPanel = fileListPanel;

        panel = new JPanel(new BorderLayout());

        JPanel labelButtonPanel = new JPanel();
        labelButtonPanel.setLayout(new BoxLayout(labelButtonPanel, BoxLayout.X_AXIS));

        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            modListLabel = new JLabel(LanguageProvider.get("gui.modlist_loading"));
            modListLabel.setMaximumSize(modListLabel.getPreferredSize());

            showModListButton = new JButton(LanguageProvider.get("gui.show_modlist_diff_button"));
            showModListButton.setEnabled(false);
            showModListButton.setToolTipText(LanguageProvider.get("gui.modlist_loading"));
            showModListButton.addActionListener(e -> showModList());

            showModListButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, showModListButton.getPreferredSize().height));

            labelButtonPanel.add(modListLabel);
            labelButtonPanel.add(Box.createHorizontalStrut(10));
            labelButtonPanel.add(showModListButton);
            labelButtonPanel.add(Box.createHorizontalGlue());
            labelButtonPanel.setBorder(BorderFactory.createTitledBorder(ModListDiff.getFirstString(false, false, null)));

            panel.add(labelButtonPanel, BorderLayout.NORTH);
        }

        JPanel bottomPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        uploadAllButton = new JButton(LanguageProvider.get("gui.upload_all_button"));
        customizeButton(uploadAllButton, "upload_all");
        uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
        uploadAllButton.addActionListener(e -> uploadAllFiles());

        uploadAllButton.setEnabled(false);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    double lestTime = (CrashAssistantApp.terminatedProcessesLocationEndTime - Instant.now().toEpochMilli() + 100) / 1000.0D;
                    uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button_delayed")
                            .replaceAll("\\$SECONDS\\$", String.format("%.3f", lestTime)));

                    if (Instant.now().toEpochMilli() >= CrashAssistantApp.terminatedProcessesLocationEndTime + 100) {
                        uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
                        uploadAllButton.setEnabled(true);
                        CrashAssistantGUI.resize();

                        Timer enableButtonsTimer = new Timer();
                        enableButtonsTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                synchronized (ControlPanel.class) {
                                    uploadButtonsActivated = true;
                                }
                                SwingUtilities.invokeLater(() -> {
                                    for (FilePanel panel : fileListPanel.filePanelList) {
                                        panel.setUploadButtonEnabled(true);
                                        panel.setWaiting(false);
                                    }
                                });
                            }
                        }, 1000);

                        this.cancel();
                    }
                });
            }
        }, 0, 21);


        gbc.gridy = 0;
        bottomPanel.add(uploadAllButton, gbc);

        requestHelpButton = new JButton(LanguageProvider.get("gui.request_help_button"));
        customizeButton(requestHelpButton, "request_help");
        requestHelpButton.addActionListener(e -> requestHelp());
        requestHelpButton.setToolTipText(PlatformHelp.getActualHelpLink());
        gbc.gridy = 1;
        bottomPanel.add(requestHelpButton, gbc);

        panel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public void customizeButton(JButton button, String button_id) {
        button.setFont(button.getFont().deriveFont(Font.BOLD,
                CrashAssistantConfig.getInteger("gui_customisation." + button_id + "_button_font_size")));
        button.setForeground(
                deserializeColor(CrashAssistantConfig.get("gui_customisation." + button_id + "_button_foreground_color"),
                        button.getForeground()));
//        button.setBackground(
//                deserializeColor(CrashAssistantConfig.get("gui_customisation." + button_id + "_button_background_color"),
//                        button.getBackground())); // todo: Swing brakes gradient if we try to customize BG color,
    }

    public Color deserializeColor(String colorString, Color fallbackColor) {
        if (colorString.equalsIgnoreCase("default")) return fallbackColor;
        try {
            int[] rgb = Arrays.stream(colorString.split("_")).mapToInt(Integer::parseInt).toArray();
            if (rgb.length != 3) {
                throw new NumberFormatException();
            }
            return new Color(rgb[0], rgb[1], rgb[2]);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to deserialize color: " + colorString + "\nExpected 3 numbers separated by underscores.", e);
        }
        return fallbackColor;
    }

    public void updateModListInfo() {
        ModListDiff modListDiff = ModListDiff.getDiff(true);
        if (!CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) return;
        if (CrashAssistantConfig.getBoolean("modpack_modlist.add_modlist_txt_as_log")) {
            Path modListTxtPath = Paths.get("logs", "modlist.txt");
            try {
                Mod.writeModlistTxt(modListTxtPath, modListDiff.getCurrentMods());
                synchronized (KnownCrashReasonMessage.class) {
                    LogsList.addIfExistsAndModified(new Log(LogType.MOD_LIST, modListTxtPath), false, false);
                }
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while saving modlist.txt", e);
            }
        }

        String labelMsg = "<html><div style='white-space:nowrap;'>";
        if (modListDiff.isEmpty()) {
            labelMsg += LanguageProvider.get("gui.modlist_not_changed_label") + ":";
            showModListButton.setEnabled(false);
            showModListButton.setToolTipText(LanguageProvider.get("gui.modlist_not_changed_label"));
        } else {
            labelMsg += LanguageProvider.get("gui.modlist_changed_label")
                    .replace("$ADDED_MODS_COUNT$", "<span style='color:green;'>" + modListDiff.getAddedMods().size() + "</span>")
                    .replace("$REMOVED_MODS_COUNT$", "<span style='color:red;'>" + modListDiff.getRemovedMods().size() + "</span>")
                    .replace("$UPDATED_MODS_COUNT$", "<span style='color:blue;'>" + modListDiff.getUpdatedMods().size() + "</span>");
            showModListButton.setEnabled(true);
            showModListButton.setToolTipText(null);
        }
        labelMsg += "</div></html>";

        modListLabel.setText(labelMsg);
        modListLabel.setMaximumSize(modListLabel.getPreferredSize());

        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            synchronized (KnownCrashReasonMessage.class) {
                SwingUtilities.invokeLater(CrashAssistantGUI::addMissingLogs);
            }
        }).start();
    }

    public JPanel getPanel() {
        return panel;
    }

    public void requestHelp() {
        try {
            stopMovingToTop = true;
            validateIsDomainTrustedAndOpenInBrowser(PlatformHelp.getActualHelpLink());
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open help_link in browser: ", e);
        }
    }

    public static void validateIsDomainTrustedAndOpenInBrowser(String link) throws URISyntaxException, IOException {
        URI uri = new URI(link);
        if (TrustedDomainsHelper.isTrustedTopDomain(uri)) {
            Desktop.getDesktop().browse(new URI(link));
            return;
        }
        String creatorWarning = "";
        if (CrashAssistantConfig.getModpackCreators().contains(ModListUtils.getCurrentUsername())) {
            creatorWarning = "\n\n<b>The next text is seen only by modpack creators</b>:\n" +
                    "If you think your domain(" + TrustedDomainsHelper.getTopDomainName(uri) + ") should be in trusted domains,\n" +
                    "please contact us on <a href =https://github.com/KostromDan/Crash-Assistant/blob/1.19.2-1.20.1/app/src/main/java/dev/kostromdan/mods/crash_assistant/app/utils/TrustedDomainsHelper.java>GitHub</a>.";
        }
        int result = JOptionPane.showConfirmDialog(
                null,
                CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.untrusted_domain_question") + "\n<a href =" + link + ">" + link + "</a>" + creatorWarning, false),
                LanguageProvider.get("gui.untrusted_domain_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) return;
        Desktop.getDesktop().browse(new URI(link));
    }


    private void showModList() {
        stopMovingToTop = true;
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setContentType("text/html");
        ModListDiff modListDiff = ModListDiff.getDiff(true);
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
                    while (!panel.isUploadButtonEnabled() && (panel.getLastError() != null || panel.isWaiting())) {
                        if (panel.isWaiting()) {
                            panel.setWaiting(false);
                            panel.setUploadButtonEnabled(true);
                            continue;
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    panel.uploadFile(false);
                }
                outerLoop:
                while (true) {
                    if (fileListPanel.filePanelList.isEmpty()) {
                        break;
                    }
                    int successCounter = 0;
                    for (FilePanel filePanel : fileListPanel.filePanelList) {
                        Log log = filePanel.getLog();
                        if (filePanel.getLastError() != null &&
                                !(filePanel.getLastError() instanceof UploadException && filePanel.getLastError().getMessage().startsWith("Crash Assistant log"))) {
                            String message = LanguageProvider.get("gui.failed_to_upload_file") + " \"" + log.getPath() + "\": " + filePanel.getLastError();
                            if (filePanel.getLastError() instanceof DeclinedException) {
                                message = filePanel.getLastError().getMessage();
                            }
                            JOptionPane.showMessageDialog(
                                    panel,
                                    message,
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
                        if (log.getLinkToUploadedFirstLines() != null) {
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
                generateMsg();
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

    public void generateMsg() {
        StringBuilder sb = new StringBuilder();
        sb.append(CrashAssistantGUI.getTitleCrashedText(true)).append(LanguageProvider.getMsgLang("msg.crashed").replace("$UPLOAD_TO$", CrashAssistantGUI.getUploadToLink())).append("\n");
        if (!CrashAssistantConfig.get("generated_message.text_under_crashed").toString().isEmpty()) {
            sb.append(CrashAssistantConfig.get("generated_message.text_under_crashed", true)).append("\n");
        }
        boolean kubeJSPosted = false;
        List<Log> kubeJSPanelList = new ArrayList<>();
        for (FilePanel panel : fileListPanel.filePanelList) {
            Log log = panel.getLog();
            if (!log.getName().startsWith("KubeJS: ")) {
                continue;
            }
            if (log.getLinkToUploadedLastLines() != null) {
                kubeJSPanelList.clear();
                break;
            }
            kubeJSPanelList.add(log);
        }
        List<String> logs = new ArrayList<>();
        for (FilePanel panel : fileListPanel.filePanelList) {
            Log log = panel.getLog();
            if (log.getName().startsWith("KubeJS: ")) {
                if (kubeJSPosted) continue;

                if (!kubeJSPanelList.isEmpty()) {
                    kubeJSPosted = true;
                    logs.add("KubeJS: " +
                            kubeJSPanelList.stream()
                                    .map(kubeJSLog -> "[" + kubeJSLog.getFileName() + "](<" + kubeJSLog.getLinkToUploadedFirstLines() + ">)")
                                    .collect(Collectors.joining(" / "))
                    );
                    continue;
                }
            }
            if (log.getType() == LogType.CRASH_ASSISTANT && !KnownCrashReasonMessage.getAllMessages().isEmpty() && !CrashAssistantConfig.getBoolean("generated_message.put_analysis_result_to_message")) {
                logs.add(formatSingleLogMessage(log) + LanguageProvider.getMsgLang("msg.found_potential_crash_reason")
                        .replaceAll("\\$COUNT\\$", Integer.toString(KnownCrashReasonMessage.getUniqueMessages().size())));
                continue;
            }
            if (log.getLinkToUploadedLastLines() == null) {
                logs.add(formatSingleLogMessage(log));
            } else {
                logs.add(panel.getMessageWithBothLinks(true));
            }
        }
        if (!LogsList.isLauncherLogExist() && FileUtils.isCurseForgeEnv()) {
            logs.add(LanguageProvider.getMsgLang("msg.skip_launcher"));
        }
        try {
            if (CrashAssistantConfig.getBoolean("generated_message.intel_corrupted_notification") && IntelCorruptedProcessorChecker.isAffectedProcessor()) {
                String model = IntelCorruptedProcessorChecker.extractModel();
                logs.add("[" + model + LanguageProvider.getMsgLang("msg.intel_corrupted_notification") + "](<" + LinksProvider.INTEL_CHIP_BUG_FAQ.getLink() + ">)");
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while checking IntelCorruptedProcessor", e);
        }

        sb.append(ModListDiff.getFilePrefix());
        if (CrashAssistantConfig.getBoolean("generated_message.one_line_logs")) {
            sb.append(String.join("   |   ", logs));
        } else {
            sb.append(String.join("\n" + ModListDiff.getFilePrefix(), logs));
        }
        sb.append("\n");

        if (CrashAssistantConfig.getBoolean("generated_message.put_problematic_frame_to_message")) {
            Optional<HsErrParsingResult> parsingResult = HsErrParser.getCachedHsErrParsingResult();
            if (parsingResult.isPresent() && parsingResult.get().getProblematicFrameFullString().isPresent()) {
                sb.append("```java\n");
                sb.append(parsingResult.get().getProblematicFrameFullString().get());
                sb.append("\n```");
            }
        }
        if (!KnownCrashReasonMessage.getAllMessages().isEmpty() && CrashAssistantConfig.getBoolean("generated_message.put_analysis_result_to_message")) {
            ModListDiffStringBuilder analysis_sb = new ModListDiffStringBuilder();
            HashMap<KnownCrashReason, List<Log>> reasonToLogs = KnownCrashReasonMessage.getUniqueMessages();
            if (!sb.toString().endsWith("\n")) {
                sb.append("\n");
            }
            analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_1"), false);
            analysis_sb.append(Integer.toString(reasonToLogs.size()), "blue", false);
            analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_2"));

            for (Map.Entry<KnownCrashReason, List<Log>> entry : reasonToLogs.entrySet()) {
                analysis_sb.append(entry.getKey().getClass().getSimpleName(), "blue", false);
                analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_in") + entry.getValue().stream()
                        .map(Log::getFileName)
                        .collect(Collectors.joining(", ")));
                if (entry.getKey() instanceof OutOfMemoryError) {
                    analysis_sb.append(LanguageProvider.getMsgLang("warnings_common.memory_args").replace("$CURRENT_MEMORY_ARGS$", ""), false);
                    analysis_sb.append("Xms: ", false);
                    analysis_sb.append(CrashAssistantApp.parentXms, "red", false);
                    analysis_sb.append(", Xmx: ", false);
                    analysis_sb.append(CrashAssistantApp.parentXmx, "green", false);
                    analysis_sb.append(", systemRAM: ", false);
                    analysis_sb.append(CrashAssistantApp.systemRAM, "blue");
                    analysis_sb.append("");
                }
            }

            sb.append(analysis_sb.toAnsi(true).trim());
        }
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            sb.append("\n");
            ModListDiff modListDiff = ModListDiff.getDiff(true);
            ModListDiffStringBuilder diffStringBuilder = modListDiff.generateDiffMsg(true);
            String modlistDIff = diffStringBuilder.toText();
            String modListDiffAnsi = diffStringBuilder.toAnsi();
            int lineCount = modlistDIff.length() - modlistDIff.replace("\n", "").length();
            if (sb.length() + modListDiffAnsi.length() >= 1650 || lineCount > 15 ||
                    (PlatformHelp.isLinkDefault() && PlatformHelp.platform == PlatformHelp.FORGE && lineCount > 3)) {
                try {
                    String link = uploadModlistDiff(modlistDIff);
                    sb.append(ModListDiff.getFilePrefix());
                    sb.append(ModListDiff.getFirstString(true, true, link));
                    sb.append("\n```");
                    if (CrashAssistantConfig.getBoolean("generated_message.color_message")) {
                        sb.append("ansi\n");
                        sb.append(LanguageProvider.getMsgLang("gui.modlist_changed_label_msg")
                                .replace("$ADDED_MODS_COUNT$", AnsiColor.GREEN.getColorPrefix() + modListDiff.getAddedMods().size() + AnsiColor.postfix)
                                .replace("$REMOVED_MODS_COUNT$", AnsiColor.RED.getColorPrefix() + modListDiff.getRemovedMods().size() + AnsiColor.postfix)
                                .replace("$UPDATED_MODS_COUNT$", AnsiColor.BLUE.getColorPrefix() + modListDiff.getUpdatedMods().size() + AnsiColor.postfix));
                    } else {
                        sb.append("\n");
                        sb.append(LanguageProvider.getMsgLang("gui.modlist_changed_label_msg")
                                .replace("$ADDED_MODS_COUNT$",  Integer.toString(modListDiff.getAddedMods().size()))
                                .replace("$REMOVED_MODS_COUNT$", Integer.toString(modListDiff.getRemovedMods().size()))
                                .replace("$UPDATED_MODS_COUNT$", Integer.toString(modListDiff.getUpdatedMods().size())));
                    }
                    sb.append("\n```");
                } catch (ExecutionException | InterruptedException | UploadException e) {
                    CrashAssistantApp.LOGGER.error("Failed to upload modlist diff message", e);
                    sb.append(modListDiffAnsi);
                }
            } else {
                sb.append(modListDiffAnsi);
            }
        }
        generatedMsg = sb.toString();
    }

    public static String formatSingleLogMessage(Log log) {
        return log.getParentName() + "[" + log.getFileName() + "](<" + log.getLinkToUploadedFirstLines() + ">)";
    }

    public static void showUploadAllButtonWarning(String warningMsg) {
        JEditorPane commentPane = CrashAssistantGUI.getEditorPane(warningMsg, false);
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

    public static String uploadModlistDiff(String diff) throws ExecutionException, InterruptedException, UploadException {
        UploadLogResponse response = ApiProvider.getMcLogsClient().uploadLog(diff).get();
        response.setClient(ApiProvider.getMcLogsClient());

        if (response.isSuccess()) {
            return CrashAssistantGUI.transformLink(response.getUrl());
        } else {
            throw new UploadException("An error occurred when uploading modlist diff: " + response.getError());
        }
    }

    public static String getCurrentMemoryArgsString() {
        return "Xms: " + CrashAssistantApp.parentXms + ", Xmx: " + CrashAssistantApp.parentXmx;
    }

    public static String getCurrentMemoryAgsMessage() {
        return LanguageProvider.getMsgLang("warnings_common.memory_args")
                .replace("$CURRENT_MEMORY_ARGS$", getCurrentMemoryArgsString());
    }
}
