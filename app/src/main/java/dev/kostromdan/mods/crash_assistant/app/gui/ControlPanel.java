package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.DeclinedException;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.OutOfMemoryError;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.HsErrParsingResult;
import dev.kostromdan.mods.crash_assistant.app.utils.*;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.ApiProvider;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.*;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.ModListDiffDialog;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ControlPanel {
    public static boolean stopMovingToTop = false;
    public static boolean uploadButtonsActivated = CrashAssistantConfig.getBoolean("general.prevent_upload_buttons_delay");
    private static boolean uploadAllButtonWarningShown = false;
    private static boolean isInsideTrustedDomainsWarning = false;
    private static JPanel panel;
    public static JDialog dialog;
    private final FileListPanel fileListPanel;
    public final UploadAllButton uploadAllButton;
    public static JButton requestHelpButton;
    public static boolean hideRequestHelpButton = false;
    private JButton showLogsToggleButton;
    private final boolean modListInitiallyVisible;
    private JPanel modListContainer;
    private String generatedMsg = null;
    private JLabel modListLabel;
    private JButton showModListButton;

    public ControlPanel(FileListPanel fileListPanel, boolean enableSimpleModeButton, Runnable simpleModeAction) {
        this.fileListPanel = fileListPanel;

        panel = new JPanel(new BorderLayout());

        JPanel labelButtonPanel = new JPanel();
        labelButtonPanel.setLayout(new BoxLayout(labelButtonPanel, BoxLayout.X_AXIS));

        boolean modListEnabled = CrashAssistantConfig.getBoolean("modpack_modlist.enabled");
        modListInitiallyVisible = modListEnabled;
        if (modListEnabled) {
            modListLabel = new JLabel(LanguageProvider.get("gui.modlist_loading")) {
                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    d.width += 4; // Add extra margin to compensate for scaling rounding issues (e.g. at 125% DPI)
                    return d;
                }
            };
            // Add a bit of left padding to the detected mods text for better spacing
            modListLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
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

            modListContainer = labelButtonPanel;
            panel.add(modListContainer, BorderLayout.NORTH);
        }

        JPanel bottomPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        uploadAllButton = new UploadAllButton(LanguageProvider.get("gui.upload_all_button"));
        customizeButton(uploadAllButton, "upload_all");
        uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
        uploadAllButton.addActionListener(e -> uploadAllFiles());

        uploadAllButton.setEnabled(false);

        if (CrashAssistantConfig.getBoolean("gui_customisation.disable_upload_all_button")) {
            uploadAllButton.setVisible(false);
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    double lestTime = (CrashAssistantApp.terminatedProcessesLocationEndTime - Instant.now().toEpochMilli() + 100) / 1000.0D;
                    uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button_delayed")
                            .replaceAll("\\$SECONDS\\$", String.format("%.0f", lestTime)));

                    if (Instant.now().toEpochMilli() >= CrashAssistantApp.terminatedProcessesLocationEndTime + 100) {
                        uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
                        uploadAllButton.setEnabled(true);
                        uploadAllButton.requestFocusInWindow();
                        uploadAllButton.setPreferredSize(uploadAllButton.getPreferredSize());
                        CrashAssistantGUI.resize();

                        if (!CrashAssistantConfig.getBoolean("general.prevent_upload_buttons_delay")) {
                            Timer enableButtonsTimer = new Timer();
                            enableButtonsTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    synchronized (ControlPanel.class) {
                                        uploadButtonsActivated = true;
                                    }
                                    SwingUtilities.invokeLater(() -> {
                                        for (FilePanel panel : fileListPanel.getFilePanelList()) {
                                            if (panel.isWaiting()) {
                                                panel.setUploadButtonEnabled(true);
                                                panel.setWaiting(false);
                                            }
                                        }
                                    });
                                }
                            }, 1000);
                        }

                        this.cancel();
                    }
                });
            }
        }, 0, 21);


        gbc.gridy = 0;
        gbc.insets = new Insets(3, 0, 0, 0);
        bottomPanel.add(uploadAllButton, gbc);

        String formulationType = CrashAssistantConfig.get("general.formulation_type");
        String suffix = formulationType.equalsIgnoreCase("GITHUB") ? ".github" : "";
        String requestHelpText = LanguageProvider.get("gui.request_help_button" + suffix);
        requestHelpButton = new JButton(formatRequestHelpButtonText(requestHelpText));
        customizeButton(requestHelpButton, "request_help");
        requestHelpButton.addActionListener(e -> requestHelp());
        requestHelpButton.setToolTipText(PlatformHelp.getActualHelpLink());
        hideReportButtonIfNeeded();
        gbc.gridy = 1;
        gbc.insets = new Insets(5, 0, 0, 0);
        bottomPanel.add(requestHelpButton, gbc);

        if (enableSimpleModeButton && simpleModeAction != null) {
            showLogsToggleButton = new JButton(formatMainButtonText(LanguageProvider.get("gui.simple_mode.button")));
            customizeButton(showLogsToggleButton, "simple_mode");
            showLogsToggleButton.addActionListener(e -> simpleModeAction.run());
            showLogsToggleButton.setToolTipText(LanguageProvider.get("gui.simple_mode.button"));
            showLogsToggleButton.setVisible(enableSimpleModeButton);
            gbc.gridy = 2;
            gbc.insets = new Insets(5, 0, 0, 0);
            bottomPanel.add(showLogsToggleButton, gbc);
        }

        panel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private static String formatRequestHelpButtonText(String text) {
        String helpName = PlatformHelp.getActualHelpName();
        int helpNameStart = text.indexOf(helpName);
        if (helpNameStart < 0) {
            return formatMainButtonText(text);
        }

        int helpNameEnd = helpNameStart + helpName.length();
        return "<html><center><b>" + escapeHtml(text.substring(0, helpNameStart))
                + "</b><b>" + escapeHtml(helpName)
                + "</b><b>" + escapeHtml(text.substring(helpNameEnd))
                + "</b></center></html>";
    }

    private static String formatMainButtonText(String text) {
        return "<html><center><b>" + escapeHtml(text) + "</b></center></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public void customizeButton(JButton button, String button_id) {
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN,
                CrashAssistantConfig.getInteger("gui_customisation." + button_id + "_button_font_size")));
        button.setForeground(
                deserializeColor(CrashAssistantConfig.get("gui_customisation." + button_id + "_button_foreground_color"),
                        button.getForeground()));
//        button.setBackground(
//                deserializeColor(CrashAssistantConfig.get("gui_customisation." + button_id + "_button_background_color"),
//                        button.getBackground())); // todo: Swing brakes gradient if we try to customize BG color,
    }

    public static Color deserializeColor(String colorString, Color fallbackColor) {
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

    public void setSimpleModeButtonVisible(boolean visible) {
        if (showLogsToggleButton == null) return;
        showLogsToggleButton.setVisible(visible);
        panel.revalidate();
        panel.repaint();
    }

    public void setModListSectionVisible(boolean visible) {
        if (modListContainer == null) return;
        modListContainer.setVisible(visible);
        panel.revalidate();
        panel.repaint();
    }

    public boolean wasModListInitiallyVisible() {
        return modListInitiallyVisible;
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
        if (isInsideTrustedDomainsWarning) return;
        if (TrustedDomainsHelper.isTrustedTopDomain(uri)) {
            LinksHelper.browse(new URI(link));
            return;
        }
        String creatorWarning = "";
        if (ModListDiff.isModpackCreator()) {
            creatorWarning = "\n\n<b>The next text is seen only by modpack creators</b>:\n" +
                    "If you think your domain(" + TrustedDomainsHelper.getTopDomainName(uri) + ") should be in trusted domains,\n" +
                    "please contact us on <a href =https://github.com/KostromDan/Crash-Assistant/blob/1.19.2-1.20.1/app/src/main/java/dev/kostromdan/mods/crash_assistant/app/utils/TrustedDomainsHelper.java>GitHub</a>.";
        }
        isInsideTrustedDomainsWarning = true;

        JEditorPane editorPane = CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.untrusted_domain_question") + "\n<a href =" + link + ">" + link + "</a>" + creatorWarning, false);
        JOptionPane optionPane = new JOptionPane(
                editorPane,
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.YES_NO_OPTION
        );
        JDialog warningDialog = optionPane.createDialog(null, LanguageProvider.get("gui.untrusted_domain_title"));

        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    URI clickedUri = e.getURL().toURI();
                    LinksHelper.browse(clickedUri);
                    if (clickedUri.toString().equals(link)) {
                        warningDialog.dispose();
                    }
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.error("Failed to open link from untrusted domain warning", ex);
                }
            }
        });

        warningDialog.setVisible(true);
        Object result = optionPane.getValue();
        isInsideTrustedDomainsWarning = false;
        if (result == null || !result.equals(JOptionPane.YES_OPTION)) return;
        LinksHelper.browse(new URI(link));
    }


    public static void showModListDiff(Window parent) {
        stopMovingToTop = true;
        ModListDiffDialog.showDialog(parent);
    }

    private void showModList() {
        showModListDiff(dialog);
    }

    private void checkAndStartUploading(boolean startUploading) {
        for (FilePanel panel : fileListPanel.getFilePanelList()) {
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
            if (startUploading) panel.uploadFile(false);
        }
    }

    private void uploadAllFiles() {
        stopMovingToTop = true;
        uploadAllButton.setEnabled(false);
        new Thread(() -> {
            if (generatedMsg == null) {
                uploadAllButton.setText(LanguageProvider.get("gui.uploading"));

                checkAndStartUploading(false);

                CompletableFuture<String> modlistDiffFuture = null;
                if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
                    ModListDiff modListDiff = ModListDiff.getDiff(true);
                    ModListDiffStringBuilder diffStringBuilder = modListDiff.generateDiffMsg(true);
                    String modlistDiffText = diffStringBuilder.toText();
                    String modListDiffAnsi = diffStringBuilder.toAnsi();
                    int lineCount = modlistDiffText.length() - modlistDiffText.replace("\n", "").length();

                    if (shouldUploadModListDiff(0, modListDiffAnsi.length(), lineCount)) {
                        modlistDiffFuture = uploadModlistDiff(modlistDiffText);
                    }
                }
                final CompletableFuture<String> finalModlistDiffFuture = modlistDiffFuture;

                checkAndStartUploading(true);

                outerLoop:
                while (true) {
                    if (fileListPanel.getFilePanelList().isEmpty()) {
                        break;
                    }
                    int successCounter = 0;
                    for (FilePanel filePanel : fileListPanel.getFilePanelList()) {
                        Log log = filePanel.getLog();
                        if (filePanel.getLastError() != null &&
                                !(filePanel.getLastError() instanceof UploadException && filePanel.getLastError().getMessage().startsWith("Crash Assistant log"))) {
                            if (!(filePanel.getLastError() instanceof DeclinedException)) {
                                synchronized (FilePanel.uploadErrorDialogLock) {
                                    if (UploadErrorDialog.isNetworkUploadError(filePanel.getLastError())) {
                                        UploadErrorDialog.show(panel, log, UploadErrorDialog.formatErrorMessage(filePanel.getLastError()));
                                    } else {
                                        String message = LanguageProvider.get("gui.failed_to_upload_file") + " \"" + log.getPath() + "\": " + filePanel.getLastError();
                                        JOptionPane.showMessageDialog(
                                                panel,
                                                message,
                                                LanguageProvider.get("gui.failed_to_upload_file") + "!",
                                                JOptionPane.ERROR_MESSAGE
                                        );
                                    }
                                }
                            }
                            uploadAllButton.setText(LanguageProvider.get("gui.error"));
                            CrashAssistantGUI.highlightButton(uploadAllButton, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_error_color"), new Color(255, 100, 100)), 2600);

                            new Timer().schedule(
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
                                            uploadAllButton.setEnabled(true);
                                            uploadAllButton.requestFocusInWindow();
                                        }
                                    },
                                    3000
                            );
                            return;
                        }
                        if (log.getLinkToUploadedFirstLines() != null) {
                            successCounter++;
                        }
                        if (successCounter == fileListPanel.getFilePanelList().size()) {
                            break outerLoop;
                        }
                        try {
                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }
                generateMsg(finalModlistDiffFuture);
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
            CrashAssistantGUI.highlightButton(uploadAllButton, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_success_color"), new Color(100, 255, 100)), buttonHighLightTime - 400);
            new Timer().schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            uploadAllButton.setText(LanguageProvider.get("gui.upload_all_finished_button"));
                            uploadAllButton.setEnabled(true);
                            uploadAllButton.requestFocusInWindow();
                        }
                    },
                    buttonHighLightTime
            );
        }).start();
    }

    public void generateMsg(CompletableFuture<String> modlistDiffFuture) {
        List<String> logs = new ArrayList<>();

        boolean kubeJSPosted = false;
        List<Log> kubeJSPanelList = new ArrayList<>();
        for (FilePanel panel : fileListPanel.getFilePanelList()) {
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

        for (FilePanel panel : fileListPanel.getFilePanelList()) {
            Log log = panel.getLog();

            if (log.getName().startsWith("KubeJS: ")) {
                if (kubeJSPosted) continue;

                if (!kubeJSPanelList.isEmpty()) {
                    kubeJSPosted = true;
                    String linkPattern = CrashAssistantConfig.get("generated_message.link_notification_pattern", false);
                    logs.add("KubeJS: " +
                            kubeJSPanelList.stream()
                                    .map(kubeJSLog -> linkPattern.replace("$TEXT$", kubeJSLog.getFileName()).replace("$LINK$", kubeJSLog.getLinkToUploadedFirstLines()))
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
                logs.add(formatSplitLogMessage(panel, log));
            }
        }

        if (!LogsList.isLauncherLogExist() && FileUtils.isCurseForgeEnv()) {
            logs.add(LanguageProvider.getMsgLang("msg.skip_launcher"));
        }

        intelCheck:
        try {
            if (CrashAssistantConfig.getBoolean("generated_message.intel_corrupted_notification")) {
                if (!IntelCorruptedProcessorChecker.isAffectedProcessor()) break intelCheck;
                String model = IntelCorruptedProcessorChecker.extractModel();
                String linkPattern = CrashAssistantConfig.get("generated_message.link_notification_pattern", false);
                logs.add(linkPattern
                        .replace("$TEXT$", model + LanguageProvider.getMsgLang("msg.intel_corrupted_notification"))
                        .replace("$LINK$", LinksProvider.INTEL_CHIP_BUG_FAQ.getLink()));
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while checking IntelCorruptedProcessor", e);
        }

        piracyCheck:
        try {
            if (CrashAssistantConfig.getBoolean("generated_message.piracy_notification")) {
                UUIDCheckStatus result = UUIDUtils.waitAndGetStatus();
                String message = null;
                switch (result) {
                    case LICENSED:
                        break piracyCheck;
                    case PIRACY_OR_OFFLINE:
                        message = LanguageProvider.getMsgLang("msg.piracy_notification");
                        break;
                    default:
                        message = LanguageProvider.getMsgLang("msg.piracy_notification_unclear");
                        break;
                }
                String uuid = UUIDUtils.getUUID();
                if (uuid != null) {
                    String linkPattern = CrashAssistantConfig.get("generated_message.link_notification_pattern", false);
                    logs.add(linkPattern
                            .replace("$TEXT$", message)
                            .replace("$LINK$", "https://mcuuid.net/?q=" + uuid));
                } else {
                    logs.add(message);
                }
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while checking IntelCorruptedProcessor", e);
        }

        String header = CrashAssistantGUI.getTitleCrashedText(true) +
                LanguageProvider.getMsgLang("msg.crashed").replace("$UPLOAD_TO$", CrashAssistantGUI.getUploadToLink()) + "\n";
        String textUnderCrashed = CrashAssistantConfig.get("generated_message.text_under_crashed", true);
        if (!textUnderCrashed.isEmpty()) textUnderCrashed += "\n";

        String prefix = ModListDiff.getFilePrefix();

        String separator = CrashAssistantConfig.get("generated_message.logs_separator", true)
                .replace("$PREFIX$", prefix);

        String joinedLogs = String.join(separator, logs);


        StringBuilder problematicFrame = new StringBuilder();
        if (CrashAssistantConfig.getBoolean("generated_message.put_problematic_frame_to_message")) {
            Optional<HsErrParsingResult> parsingResult = HsErrParser.getCachedHsErrParsingResult();
            if (parsingResult.isPresent() && parsingResult.get().getProblematicFrameFullString().isPresent()) {
                String framePattern = CrashAssistantConfig.get("generated_message.problematic_frame_pattern", true);
                problematicFrame.append(framePattern.replace("$CONTENT$", parsingResult.get().getProblematicFrameFullString().get()));
            }
        }

        StringBuilder analysisResult = new StringBuilder();
        if (!KnownCrashReasonMessage.getAllMessages().isEmpty() && CrashAssistantConfig.getBoolean("generated_message.put_analysis_result_to_message")) {
            ModListDiffStringBuilder analysis_sb = new ModListDiffStringBuilder();
            HashMap<KnownCrashReason, List<Log>> reasonToLogs = KnownCrashReasonMessage.getUniqueMessages();

            analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_1"), false);
            analysis_sb.append(Integer.toString(reasonToLogs.size()), "blue", false);
            analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_2"));

            int scriptedResultsCount = (int) KnownCrashReasonMessage.getAllMessages().stream()
                    .filter(msg -> msg.getReason() instanceof ScriptedAnalysis)
                    .count();

            for (Map.Entry<KnownCrashReason, List<Log>> entry : reasonToLogs.entrySet()) {
                if (entry.getKey() instanceof ScriptedAnalysis) continue;

                analysis_sb.append(entry.getKey().getClass().getSimpleName(), "blue", false);
                analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_in") + entry.getValue().stream()
                        .map(Log::getFileName)
                        .distinct()
                        .collect(Collectors.joining(", ")));
                if (entry.getKey() instanceof OutOfMemoryError) {
                    analysis_sb.append(LanguageProvider.getMsgLang("warnings_common.memory_args").replace("$CURRENT_MEMORY_ARGS$", ""), false);
                    analysis_sb.append("Xms: ", false);
                    analysis_sb.append(CrashAssistantApp.minecraftXms, "red", false);
                    analysis_sb.append(", Xmx: ", false);
                    analysis_sb.append(CrashAssistantApp.minecraftXmx, "green", false);
                    analysis_sb.append(", systemRAM: ", false);
                    analysis_sb.append(CrashAssistantApp.systemRAM, "blue");
                    analysis_sb.append("");
                }
            }
            if (scriptedResultsCount > 0) {
                analysis_sb.append(LanguageProvider.getMsgLang("msg.scripted_analysis"), "blue", false);
                analysis_sb.append(": " + scriptedResultsCount + " " + LanguageProvider.getMsgLang("msg.scripted_analysis_results"));
            }
            String ansiAnalysis = analysis_sb.toAnsi(true).trim();
            if (!ansiAnalysis.isEmpty()) {
                if (!problematicFrame.toString().isEmpty()) analysisResult.append("\n");
                analysisResult.append(ansiAnalysis);
            }
        }


        String finalStructure = CrashAssistantConfig.get("generated_message.message_structure", false);
        String partialMsg = finalStructure
                .replace("$HEADER$", header)
                .replace("$TEXT_UNDER_CRASHED$", textUnderCrashed)
                .replace("$PREFIX$", prefix)
                .replace("$LOGS$", joinedLogs)
                .replace("$PROBLEMATIC_FRAME$", problematicFrame.toString())
                .replace("$ANALYSIS_RESULT$", analysisResult.toString());

        StringBuilder modListDiffContent = new StringBuilder();
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            ModListDiff modListDiff = ModListDiff.getDiff(true);
            ModListDiffStringBuilder diffStringBuilder = modListDiff.generateDiffMsg(true);
            String modlistDiffText = diffStringBuilder.toText();
            String modListDiffAnsi = diffStringBuilder.toAnsi();
            int lineCount = modlistDiffText.length() - modlistDiffText.replace("\n", "").length();

            int currentMsgLength = partialMsg.replace("$MODLIST_DIFF$", "").length();

            if (modlistDiffFuture != null || shouldUploadModListDiff(currentMsgLength, modListDiffAnsi.length(), lineCount)) {
                try {
                    if (modlistDiffFuture == null) {
                        modlistDiffFuture = uploadModlistDiff(modlistDiffText);
                    }
                    String link = modlistDiffFuture.get();

                    String summaryMsgKey = "gui.modlist_changed_label_msg";
                    String summaryContent;
                    if (CrashAssistantConfig.getBoolean("generated_message.color_message")) {
                        summaryContent = LanguageProvider.getMsgLang(summaryMsgKey)
                                .replace("$ADDED_MODS_COUNT$", AnsiColor.GREEN.getColorPrefix() + modListDiff.getAddedMods().size() + AnsiColor.postfix)
                                .replace("$REMOVED_MODS_COUNT$", AnsiColor.RED.getColorPrefix() + modListDiff.getRemovedMods().size() + AnsiColor.postfix)
                                .replace("$UPDATED_MODS_COUNT$", AnsiColor.BLUE.getColorPrefix() + modListDiff.getUpdatedMods().size() + AnsiColor.postfix);
                    } else {
                        summaryContent = LanguageProvider.getMsgLang(summaryMsgKey)
                                .replace("$ADDED_MODS_COUNT$", Integer.toString(modListDiff.getAddedMods().size()))
                                .replace("$REMOVED_MODS_COUNT$", Integer.toString(modListDiff.getRemovedMods().size()))
                                .replace("$UPDATED_MODS_COUNT$", Integer.toString(modListDiff.getUpdatedMods().size()));
                    }

                    String pattern = CrashAssistantConfig.get("generated_message.ansi_block_pattern", false);
                    String filePrefix = ModListDiff.getFilePrefix();
                    String firstString = ModListDiff.getFirstString(true, true, link);

                    modListDiffContent.append("\n");
                    modListDiffContent.append(pattern
                            .replace("$PREFIX$", filePrefix)
                            .replace("$HEADER$", firstString)
                            .replace("$CONTENT$", summaryContent));

                } catch (ExecutionException | InterruptedException | UploadException e) {
                    CrashAssistantApp.LOGGER.error("Failed to upload modlist diff message", e);
                    if (modListDiffContent.length() == 0) modListDiffContent.append("\n");
                    modListDiffContent.append(modListDiffAnsi);
                }
            } else {
                if (modListDiffContent.length() == 0) modListDiffContent.append("\n");
                modListDiffContent.append(modListDiffAnsi);
            }
        }

        generatedMsg = partialMsg
                .replace("$MODLIST_DIFF$", modListDiffContent.toString());

        CrashAssistantApp.LOGGER.info("Generated message successfully:\n\n\n" + generatedMsg + "\n\n\n");
    }


    public static String formatSingleLogMessage(Log log) {
        String pattern = CrashAssistantConfig.get("generated_message.log_line_pattern", true);
        return pattern
                .replace("$LOG_NAME$", log.getParentName())
                .replace("$FILE_NAME$", log.getFileName())
                .replace("$LINK$", log.getLinkToUploadedFirstLines());
    }

    public static String formatSplitLogMessage(FilePanel panel, Log log) {
        String pattern = CrashAssistantConfig.get("generated_message.log_line_split_pattern", true);
        return pattern
                .replace("$LOG_NAME$", log.getParentName())
                .replace("$FILE_NAME$", log.getFileName())
                .replace("$LINK_FIRST_LINES$", log.getLinkToUploadedFirstLines())
                .replace("$LINK_LAST_LINES$", log.getLinkToUploadedLastLines())
                .replace("$TOO_BIG_REASONS$", panel.getTooBigReasons(true));
    }

    private static boolean shouldUploadModListDiff(int currentMsgLength, int diffLength, int lineCount) {
        return currentMsgLength + diffLength >= 1650 || lineCount > 15 ||
                (PlatformHelp.isLinkDefault() && PlatformHelp.platform == PlatformHelp.FORGE && lineCount > 3);
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

    public static CompletableFuture<String> uploadModlistDiff(String diff) {
        return CompletableFuture.supplyAsync(() -> {
            if (!PrivacyPolicyDialog.ensurePrivacyPolicyAccepted()) {
                throw new DeclinedException(LanguageProvider.get("gui.privacy.declined"));
            }
            return null;
        }).thenCompose(ignored -> ApiProvider.getMcLogsClient().uploadLog("ModList Diff", diff).thenApply(response -> {
            if (response.isSuccess()) {
                String finalLink = CrashAssistantGUI.transformLink(response.getUrl());
                CrashAssistantApp.LOGGER.info("Modlist diff uploaded successfully: " + finalLink);
                MclogArrayRegistrar.registerUploadedLog(
                        response.getId(),
                        response.getCreated(),
                        "mod_list_diff.txt",
                        "MOD_LIST_DIFF",
                        MclogArrayRegistrar.LAST_PRIORITY);
                return finalLink;
            } else {
                if (response.isNetworkError()) {
                    throw UploadException.network("An error occurred when uploading modlist diff: " + response.getError());
                }
                throw new UploadException("An error occurred when uploading modlist diff: " + response.getError());
            }
        }));
    }

    public static String getCurrentMemoryArgsString() {
        return "Xms: " + CrashAssistantApp.minecraftXms + ", Xmx: " + CrashAssistantApp.minecraftXmx;
    }

    public static String getCurrentMemoryAgsMessage() {
        return LanguageProvider.getMsgLang("warnings_common.memory_args")
                .replace("$CURRENT_MEMORY_ARGS$", getCurrentMemoryArgsString());
    }


    public static class UploadAllButton extends JButton {
        static String uploadAllText = LanguageProvider.get("gui.upload_all_button");
        static String copyAllText = LanguageProvider.get("gui.upload_all_finished_button");

        public UploadAllButton(String text) {
            super(processTextBeforeChange(text));
        }

        @Override
        public void setText(String text) {
            super.setText(processTextBeforeChange(text));
        }

        private static String processTextBeforeChange(String text) {
            if (text == null || text.startsWith("<html>")) {
                return text;
            }
            String escapedText = escapeHtml(text);
            if (text.equals(uploadAllText) || text.equals(copyAllText)) {
                escapedText = splitIntoTwoLines(escapedText);
            }
            return "<html><center><b>" + escapedText + "</b></center></html>";
        }

        private static String splitIntoTwoLines(String text) {
            if (true) return text; // now we use short formulation as an experiment, so this not needed.

            int length = text.length();
            // Bias the midpoint about 10% toward the right
            int biasedMid = (int) (length * 0.50);
            int bestSpace = -1;
            int minDistance = Integer.MAX_VALUE;

            // Find the space nearest to the biased midpoint
            for (int i = 0; i < length; i++) {
                if (text.charAt(i) == ' ') {
                    int distance = Math.abs(biasedMid - i);
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestSpace = i;
                    }
                }
            }

            // If no space found, return as-is
            if (bestSpace == -1) {
                return text;
            }

            // Replace the chosen space with <br> for HTML rendering
            return text.substring(0, bestSpace) + "<br>" + text.substring(bestSpace + 1);
        }
    }

    public static void hideReportButtonIfNeeded() {
        if (requestHelpButton != null && hideRequestHelpButton) requestHelpButton.setVisible(false);
    }
}
