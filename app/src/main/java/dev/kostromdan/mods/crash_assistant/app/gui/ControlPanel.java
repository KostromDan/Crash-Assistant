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
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListComparisonReference;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryManager;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryRecord;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistorySummary;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.GeneratedMessage;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.ModListComparison;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.ModListDiffDialog;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.ModListDiffWidget;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.history.ModListHistoryDialog;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ControlPanel {
    public static volatile boolean stopMovingToTop = false;
    public static boolean uploadButtonsActivated = CrashAssistantConfig.getBoolean("general.prevent_upload_buttons_delay");
    private static boolean uploadAllButtonWarningShown = false;
    private static boolean isInsideTrustedDomainsWarning = false;
    private static JPanel panel;
    public static JDialog dialog;
    private final FileListPanel fileListPanel;
    public final UploadAllButton uploadAllButton;
    private final JButton uploadArrayBrowserButton;
    private final JPanel uploadAllRow;
    public static JButton requestHelpButton;
    public static boolean hideRequestHelpButton = false;
    private JButton showLogsToggleButton;
    private final boolean modListInitiallyVisible;
    private boolean initialUploadAllGateOpened;
    private boolean individualUploadButtonsActivationScheduled;
    private JPanel modListContainer;
    private ModListDiffWidget modpackModListWidget;
    private ModListDiffWidget historyModListWidget;
    private volatile GeneratedMessageComparison generatedMessageComparison;
    private String generatedMsg = null;
    private List<FilePanel> generatedMsgBatch = Collections.emptyList();
    private long generatedMsgScriptRevision = -1;
    private String uploadArrayViewerLink;
    private boolean uploadAllMessageCopied;

    public ControlPanel(FileListPanel fileListPanel, boolean enableSimpleModeButton, Runnable simpleModeAction) {
        this.fileListPanel = fileListPanel;

        panel = new JPanel(new BorderLayout());

        boolean modListEnabled = CrashAssistantConfig.getBoolean("modpack_modlist.enabled");
        modListInitiallyVisible = modListEnabled;
        if (modListEnabled) {
            modListContainer = new JPanel(new GridBagLayout());
            modListContainer.setBorder(BorderFactory.createTitledBorder(
                    LanguageProvider.get("gui.modlist_section_title")));
            int modListRow = 0;

            boolean actualModpack = !PlatformHelp.isLinkDefault();
            boolean modpackCreator = ModListDiff.isModpackCreator();
            if (actualModpack && !modpackCreator) {
                modpackModListWidget = new ModListDiffWidget(
                        () -> showComparison(modpackModListWidget));
                modpackModListWidget.setLoading(getModpackComparisonSourceLabel());
                modpackModListWidget.addTo(modListContainer, modListRow++);
            }

            historyModListWidget = new ModListDiffWidget(
                    () -> showComparison(historyModListWidget));
            historyModListWidget.setLoading(getLatestLaunchComparisonSourceLabel());
            historyModListWidget.addTo(modListContainer, modListRow++);

            JButton showModListHistoryButton = new JButton(
                    LanguageProvider.get("gui.show_modlist_history_button"));
            showModListHistoryButton.addActionListener(event -> showModListHistory());
            GridBagConstraints historyButtonConstraints = new GridBagConstraints();
            historyButtonConstraints.gridx = 0;
            historyButtonConstraints.gridy = modListRow;
            historyButtonConstraints.gridwidth = GridBagConstraints.REMAINDER;
            historyButtonConstraints.anchor = GridBagConstraints.LINE_START;
            historyButtonConstraints.insets = new Insets(6, 5, 4, 5);
            modListContainer.add(showModListHistoryButton, historyButtonConstraints);
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

        uploadArrayBrowserButton = createUploadArrayBrowserButton();
        uploadAllRow = new JPanel(new BorderLayout(5, 0));
        uploadAllRow.add(uploadAllButton, BorderLayout.CENTER);
        uploadAllRow.add(uploadArrayBrowserButton, BorderLayout.EAST);
        listenForFirstUploadArrayRegistration();

        if (CrashAssistantConfig.getBoolean("gui_customisation.disable_upload_all_button")) {
            uploadAllRow.setVisible(false);
        }

        startInitialUploadAllTimer();

        gbc.gridy = 0;
        gbc.insets = new Insets(3, 0, 0, 0);
        bottomPanel.add(uploadAllRow, gbc);

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

    private void startInitialUploadAllTimer() {
        javax.swing.Timer timer = new javax.swing.Timer(21, e -> {
            refreshInitialUploadAllState();
            if (initialUploadAllGateOpened) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
        });
        timer.setInitialDelay(0);
        timer.start();
    }

    void refreshInitialUploadAllState() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshInitialUploadAllState);
            return;
        }
        if (initialUploadAllGateOpened) {
            return;
        }

        long activationTime = CrashAssistantApp.terminatedProcessesLocationEndTime + 100;
        long currentTime = Instant.now().toEpochMilli();
        if (currentTime < activationTime) {
            double timeLeft = (activationTime - currentTime) / 1000.0D;
            uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button_delayed")
                    .replaceAll("\\$SECONDS\\$", String.format("%.0f", timeLeft)));
            return;
        }

        scheduleIndividualUploadButtonsActivation();

        boolean analysisDeadlineReached = LogAnalyser.isAnalysisDeadlineReached();
        if (analysisDeadlineReached) {
            try {
                LogAnalyser.stopTimedOutAnalysisForUpload();
            } catch (Throwable throwable) {
                CrashAssistantApp.LOGGER.error("Failed to stop timed out log analysis.", throwable);
            }
        }

        if (!analysisDeadlineReached
                && (!CrashAssistantGUI.isInitialLogAnalysisFinished()
                || !CrashAssistantApp.isTerminatedProcessesLocationFinished())) {
            uploadAllButton.setEnabled(false);
            String delayedText = LanguageProvider.get("gui.upload_all_button_analysis_delayed");
            String previousText = uploadAllButton.getText();
            uploadAllButton.setText(delayedText);
            if (!Objects.equals(previousText, uploadAllButton.getText())) {
                CrashAssistantGUI.resize();
            }
            return;
        }

        CrashAssistantGUI.addMissingLogs();
        initialUploadAllGateOpened = true;
        uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
        uploadAllButton.setEnabled(true);
        uploadAllButton.requestFocusInWindow();
        uploadAllButton.setPreferredSize(uploadAllButton.getPreferredSize());
        CrashAssistantGUI.resize();
    }

    private void scheduleIndividualUploadButtonsActivation() {
        if (individualUploadButtonsActivationScheduled) {
            return;
        }
        individualUploadButtonsActivationScheduled = true;
        if (CrashAssistantConfig.getBoolean("general.prevent_upload_buttons_delay")) {
            return;
        }

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

    private JButton createUploadArrayBrowserButton() {
        JButton button = new JButton();
        button.setFont(uploadAllButton.getFont());
        button.setToolTipText(LanguageProvider.get("gui.browser_button_tooltip"));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.addActionListener(e -> openUploadArrayInBrowser());

        int side = uploadAllButton.getPreferredSize().height;
        Dimension size = new Dimension(side, side);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        SvgButtonIcon.install(button, GeneratedInternetIcon.ICON);
        button.setVisible(false);
        return button;
    }

    private void listenForFirstUploadArrayRegistration() {
        MclogArrayRegistrar.onFirstSuccessfulArrayRegistration(arrayId ->
                SwingUtilities.invokeLater(() -> {
                    uploadArrayViewerLink = "https://paste.kostromdan.dev/mclogsarray/" + arrayId;
                    updateUploadArrayBrowserButtonVisibility();
                }));
    }

    private void updateUploadArrayBrowserButtonVisibility() {
        boolean visible = uploadAllMessageCopied && uploadArrayViewerLink != null;
        uploadArrayBrowserButton.setVisible(visible);
        uploadAllRow.revalidate();
        uploadAllRow.repaint();
    }

    private void openUploadArrayInBrowser() {
        stopMovingToTop = true;
        if (uploadArrayViewerLink == null) {
            return;
        }
        try {
            LinksHelper.browse(new URI(uploadArrayViewerLink));
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open uploaded log array in browser", e);
        }
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
        if (!CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) return;
        if (CrashAssistantConfig.getBoolean("modpack_modlist.add_modlist_txt_as_log")
                && ModListHistoryManager.wasModListTxtGenerated()) {
            Path modListTxtPath = Paths.get("logs", "modlist.txt");
            synchronized (KnownCrashReasonMessage.class) {
                LogsList.addIfExistsAndModified(new Log(LogType.MOD_LIST, modListTxtPath), false, false);
            }
        }

        LinkedHashSet<Mod> currentMods = ModListUtils.getCurrentModList(true);
        ModListComparison modpackComparison = null;
        boolean modpackModListMissing = modpackModListWidget != null
                && Files.notExists(ModListUtils.getSavedModListPath());
        if (modpackModListWidget != null && !modpackModListMissing) {
            String title = getModpackComparisonTitle(false);
            modpackComparison = ModListComparison.againstCurrent(
                    title,
                    LanguageProvider.get("gui.modlist_diff.column.file"),
                    ModListComparison.SourceKind.MODPACK_BASELINE,
                    ModListUtils.getSavedModList(),
                    LanguageProvider.get("gui.modlist_diff.column.file_current"),
                    currentMods
            );
        }

        ModListComparison historyComparison = null;
        String historyTitle = getLatestLaunchComparisonTitle(false);
        String historyMessageTitle = getLatestLaunchComparisonTitle(true);
        String historyMessagePart1 = getLatestLaunchComparisonPart1(true);
        String historyMessagePart2 = getLatestLaunchComparisonPart2(true);
        String historySourceLabel = getLatestLaunchComparisonSourceLabel();
        Optional<ModListHistorySummary> currentRecord = ModListHistoryManager.getCurrentSummary();
        if (currentRecord.isPresent()) {
            Optional<ModListComparisonReference> reference = ModListHistoryManager.getStore()
                    .findComparisonReference(currentRecord.get());
            if (reference.isPresent()) {
                ModListComparisonReference resolved = reference.get();
                boolean duplicatedModpackBaseline = !PlatformHelp.isLinkDefault()
                        && !ModListDiff.isModpackCreator()
                        && resolved.getKind() == ModListComparisonReference.Kind.LEGACY_SNAPSHOT;
                if (!duplicatedModpackBaseline) {
                    historyTitle = getHistoryComparisonTitle(resolved.getKind(), false);
                    historyMessageTitle = getHistoryComparisonTitle(resolved.getKind(), true);
                    historyMessagePart1 = getHistoryComparisonPart1(resolved.getKind(), true);
                    historyMessagePart2 = getHistoryComparisonPart2(resolved.getKind(), true);
                    historySourceLabel = getHistoryComparisonSourceLabel(resolved.getKind());
                    historyComparison = createHistoryComparison(
                            historyTitle, resolved, currentMods);
                }
            }
        }

        final ModListComparison finalModpackComparison = modpackComparison;
        final ModListComparison finalHistoryComparison = historyComparison;
        final ModListDiff finalModpackDiff = modpackComparison == null
                ? null
                : modpackComparison.createDiff();
        final ModListDiff finalHistoryDiff = historyComparison == null
                ? null
                : historyComparison.createDiff();
        final boolean finalModpackModListMissing = modpackModListMissing;
        final String finalHistorySourceLabel = historySourceLabel;
        SwingEDT.runAndWait(() -> {
            if (modpackModListWidget != null) {
                if (finalModpackModListMissing) {
                    modpackModListWidget.setUnavailable(
                            getModpackComparisonSourceLabel(),
                            LanguageProvider.get("gui.modlist_comparison.modpack_modlist_missing"));
                } else if (finalModpackComparison != null) {
                    modpackModListWidget.setComparison(
                            getModpackComparisonSourceLabel(),
                            finalModpackComparison,
                            finalModpackDiff);
                }
            }
            if (finalHistoryComparison == null) {
                historyModListWidget.setUnavailable(finalHistorySourceLabel);
            } else {
                historyModListWidget.setComparison(
                        finalHistorySourceLabel,
                        finalHistoryComparison,
                        finalHistoryDiff);
            }
            modListContainer.revalidate();
            modListContainer.repaint();
        });

        if (modpackModListWidget != null) {
            generatedMessageComparison = new GeneratedMessageComparison(
                    modpackComparison,
                    getModpackComparisonTitle(true),
                    getModpackComparisonPart1(true),
                    getModpackComparisonPart2(true),
                    modpackModListMissing
                            ? LanguageProvider.getMsgLang("gui.modlist_comparison.modpack_modlist_missing")
                            : null);
        } else {
            generatedMessageComparison = new GeneratedMessageComparison(
                    historyComparison,
                    historyMessageTitle,
                    historyMessagePart1,
                    historyMessagePart2,
                    null);
        }

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

    private static String getModpackComparisonTitle(boolean forMsg) {
        return getModpackComparisonPart1(forMsg) + getModpackComparisonPart2(forMsg);
    }

    private static String getModpackComparisonPart1(boolean forMsg) {
        return LanguageProvider.getLangFunction(forMsg).apply("msg.modlist_changes_modpack_1");
    }

    private static String getModpackComparisonPart2(boolean forMsg) {
        return LanguageProvider.getLangFunction(forMsg).apply("msg.modlist_changes_modpack_2");
    }

    private static String getLatestLaunchComparisonTitle(boolean forMsg) {
        return getLatestLaunchComparisonPart1(forMsg) + getLatestLaunchComparisonPart2(forMsg);
    }

    private static String getLatestLaunchComparisonPart1(boolean forMsg) {
        return LanguageProvider.getLangFunction(forMsg).apply("msg.modlist_changes_latest_launch_1");
    }

    private static String getLatestLaunchComparisonPart2(boolean forMsg) {
        return LanguageProvider.getLangFunction(forMsg).apply("msg.modlist_changes_latest_launch_2");
    }

    private static String getModpackComparisonSourceLabel() {
        return LanguageProvider.get("gui.modlist_comparison.modpack");
    }

    private static String getLatestLaunchComparisonSourceLabel() {
        return LanguageProvider.get("gui.modlist_comparison.latest_launch");
    }

    private static String getHistoryComparisonSourceLabel(ModListComparisonReference.Kind kind) {
        switch (kind) {
            case JOINED:
                return LanguageProvider.get("gui.modlist_comparison.latest_world_join");
            case LEGACY_SNAPSHOT:
                return LanguageProvider.get("gui.modlist_comparison.legacy_snapshot");
            case TITLE_SCREEN:
            default:
                return getLatestLaunchComparisonSourceLabel();
        }
    }

    private static String getHistoryComparisonTitle(ModListComparisonReference.Kind kind, boolean forMsg) {
        return getHistoryComparisonPart1(kind, forMsg)
                + getHistoryComparisonPart2(kind, forMsg);
    }

    private static String getHistoryComparisonPart1(ModListComparisonReference.Kind kind, boolean forMsg) {
        Function<String, String> lang = LanguageProvider.getLangFunction(forMsg);
        switch (kind) {
            case JOINED:
                return lang.apply("gui.modlist_changes_latest_world_join_1");
            case LEGACY_SNAPSHOT:
                return lang.apply("gui.modlist_changes_legacy_snapshot_1");
            case TITLE_SCREEN:
            default:
                return getLatestLaunchComparisonPart1(forMsg);
        }
    }

    private static String getHistoryComparisonPart2(ModListComparisonReference.Kind kind, boolean forMsg) {
        Function<String, String> lang = LanguageProvider.getLangFunction(forMsg);
        switch (kind) {
            case JOINED:
                return lang.apply("gui.modlist_changes_latest_world_join_2");
            case LEGACY_SNAPSHOT:
                return lang.apply("gui.modlist_changes_legacy_snapshot_2");
            case TITLE_SCREEN:
            default:
                return getLatestLaunchComparisonPart2(forMsg);
        }
    }

    private static String formatHistoryReferenceLabel(ModListHistoryRecord record) {
        if (record.isLegacySnapshot()) {
            return LanguageProvider.get("gui.modlist_history.legacy_snapshot");
        }
        return Instant.ofEpochMilli(record.getTimestamp()).toString();
    }

    private static ModListComparison createHistoryComparison(
            String title,
            ModListComparisonReference reference,
            LinkedHashSet<Mod> currentMods) {
        ModListHistoryRecord record = reference.getRecord();
        String referenceLabel = formatHistoryReferenceLabel(record);
        String currentLabel = LanguageProvider.get("gui.modlist_diff.column.file_current");
        if (reference.getKind() == ModListComparisonReference.Kind.LEGACY_SNAPSHOT) {
            // Its launch outcome is unknown, but it is still a valid reference
            // snapshot for the normal comparison against the live installation.
            return ModListComparison.againstCurrent(
                    title,
                    referenceLabel,
                    ModListComparison.SourceKind.LEGACY_SNAPSHOT,
                    record.getMods(),
                    currentLabel,
                    currentMods);
        }
        return ModListComparison.againstCurrent(
                title,
                referenceLabel,
                ModListComparison.SourceKind.HISTORY,
                record.getMods(),
                currentLabel,
                currentMods);
    }

    public JPanel getPanel() {
        return panel;
    }

    public void setSimpleModeButtonVisible(boolean visible) {
        SwingEDT.runAndWait(() -> {
            if (showLogsToggleButton == null) return;
            showLogsToggleButton.setVisible(visible);
            panel.revalidate();
            panel.repaint();
        });
    }

    public void setModListSectionVisible(boolean visible) {
        SwingEDT.runAndWait(() -> {
            if (modListContainer == null) return;
            modListContainer.setVisible(visible);
            panel.revalidate();
            panel.repaint();
        });
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
        final Window owner = parent == null ? CrashAssistantGUI.getFrame() : parent;
        if (owner != null) {
            owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        Thread loader = new Thread(() -> {
            final ModListComparison comparison = resolveDefaultModListComparison();
            SwingUtilities.invokeLater(() -> {
                if (owner != null) {
                    owner.setCursor(Cursor.getDefaultCursor());
                }
                if (comparison == null) {
                    JOptionPane.showMessageDialog(
                            owner,
                            LanguageProvider.get("gui.modlist_history.no_comparison_reference"),
                            LanguageProvider.get("gui.modlist_history.comparison_title"),
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                ModListDiffDialog.showDialog(owner, comparison);
            });
        }, "modlist-default-comparison-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Resolves the same source as the visible primary diff widget. Scripted
     * warnings also use this entry point, so they must not fall back to the
     * migrated/absent standalone modlist.json.
     */
    private static ModListComparison resolveDefaultModListComparison() {
        ModListUtils.ModListScanResult scan = ModListUtils.scanCurrentModListResult(false);
        if (!scan.isSuccessful()) {
            return null;
        }
        LinkedHashSet<Mod> currentMods = scan.getMods();
        if (!PlatformHelp.isLinkDefault() && !ModListDiff.isModpackCreator()) {
            String title = getModpackComparisonTitle(false);
            return ModListComparison.againstCurrent(
                    title,
                    LanguageProvider.get("gui.modlist_diff.column.file"),
                    ModListComparison.SourceKind.MODPACK_BASELINE,
                    ModListUtils.getSavedModList(),
                    LanguageProvider.get("gui.modlist_diff.column.file_current"),
                    currentMods);
        }

        Optional<ModListHistorySummary> currentRecord = ModListHistoryManager.getCurrentSummary();
        if (!currentRecord.isPresent()) {
            return null;
        }
        Optional<ModListComparisonReference> reference = ModListHistoryManager.getStore()
                .findComparisonReference(currentRecord.get());
        if (!reference.isPresent()) {
            return null;
        }
        ModListComparisonReference resolved = reference.get();
        return createHistoryComparison(
                getHistoryComparisonTitle(resolved.getKind(), false),
                resolved,
                currentMods);
    }

    private void showComparison(ModListDiffWidget widget) {
        final ModListComparison comparison = widget == null ? null : widget.getComparison();
        if (comparison == null) return;
        stopMovingToTop = true;
        final Window owner = getModListOwner();
        if (!comparison.isCurrentInstallationEditable()) {
            ModListDiffDialog.showDialog(owner, comparison);
            return;
        }
        if (owner != null) {
            owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        Thread loader = new Thread(() -> {
            final ModListUtils.ModListScanResult scan = ModListUtils.scanCurrentModListResult(false);
            SwingUtilities.invokeLater(() -> {
                if (owner != null) {
                    owner.setCursor(Cursor.getDefaultCursor());
                }
                if (!scan.isSuccessful()) {
                    JOptionPane.showMessageDialog(
                            owner,
                            LanguageProvider.get("gui.modlist_history.load_error")
                                    .replace("$ERROR$", "Failed to scan the current mods folder."),
                            LanguageProvider.get("gui.modlist_history.title"),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                ModListDiffDialog.showDialog(owner, comparison.withFreshCurrent(scan.getMods()));
            });
        }, "modlist-current-refresh");
        loader.setDaemon(true);
        loader.start();
    }

    private void showModListHistory() {
        stopMovingToTop = true;
        ModListHistoryDialog.showDialog(getModListOwner());
    }

    private Window getModListOwner() {
        Window owner = modListContainer == null
                ? null
                : SwingUtilities.getWindowAncestor(modListContainer);
        return owner == null ? CrashAssistantGUI.getFrame() : owner;
    }

    private void checkAndStartUploading(List<FilePanel> uploadBatch, boolean startUploading) {
        for (FilePanel panel : uploadBatch) {
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
            if (startUploading) {
                if (panel.getLog().getType() == LogType.CRASH_ASSISTANT) {
                    boolean batchUploadStarted = false;
                    while (!batchUploadStarted) {
                        while (panel.isUploadInProgress() || !panel.isUploadButtonEnabled()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }
                        batchUploadStarted = panel.uploadFile(false, uploadBatch);
                    }
                } else {
                    panel.uploadFile(false, uploadBatch);
                }
            }
        }
    }

    private void uploadAllFiles() {
        stopMovingToTop = true;
        uploadAllButton.setEnabled(false);
        new Thread(() -> {
            List<FilePanel> uploadBatch = Collections.unmodifiableList(
                    new ArrayList<>(fileListPanel.getFilePanelList())
            );
            if (generatedMsg != null && (!generatedMsgBatch.equals(uploadBatch)
                    || generatedMsgScriptRevision != GeneratedMessage.getRevision())) {
                generatedMsg = null;
            }
            if (generatedMsg == null) {
                SwingEDT.runAndWait(() -> uploadAllButton.setText(LanguageProvider.get("gui.uploading")));

                checkAndStartUploading(uploadBatch, false);

                CompletableFuture<String> modlistDiffFuture = null;
                GeneratedMessageComparison generatedComparison = generatedMessageComparison;
                ModListComparison comparison = generatedComparison == null
                        ? null
                        : generatedComparison.comparison;
                String comparisonTitle = generatedComparison == null
                        ? null
                        : generatedComparison.title;
                if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled") && generatedComparison != null) {
                    if (comparison == null) {
                        String noComparisonText = createNoComparisonDiffMessage(
                                comparisonTitle,
                                generatedComparison.noComparisonMessage).toText();
                        modlistDiffFuture = uploadModlistDiff(noComparisonText);
                    } else {
                        ModListDiff modListDiff = comparison.createDiff();
                        if (!modListDiff.isEmpty()) {
                            ModListDiffStringBuilder diffStringBuilder = modListDiff.generateDiffMsg(true, comparisonTitle);
                            String modlistDiffText = diffStringBuilder.toText();
                            modlistDiffFuture = uploadModlistDiff(modlistDiffText);
                        }
                    }
                }
                final CompletableFuture<String> finalModlistDiffFuture = modlistDiffFuture;

                checkAndStartUploading(uploadBatch, true);

                outerLoop:
                while (true) {
                    if (uploadBatch.isEmpty()) {
                        break;
                    }
                    int successCounter = 0;
                    for (FilePanel filePanel : uploadBatch) {
                        Log log = filePanel.getLog();
                        if (filePanel.getLastError() != null &&
                                !(filePanel.getLastError() instanceof UploadException && filePanel.getLastError().getMessage().startsWith("Crash Assistant log"))) {
                            if (!(filePanel.getLastError() instanceof DeclinedException)) {
                                synchronized (FilePanel.uploadErrorDialogLock) {
                                    SwingEDT.runAndWait(() -> {
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
                                    });
                                }
                            }
                            SwingEDT.runAndWait(() -> {
                                uploadAllButton.setText(LanguageProvider.get("gui.error"));
                                CrashAssistantGUI.highlightButton(uploadAllButton, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_error_color"), new Color(255, 100, 100)), 2600);
                            });

                            new Timer().schedule(
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            SwingEDT.runAndWait(() -> {
                                                uploadAllButton.setText(LanguageProvider.get("gui.upload_all_button"));
                                                uploadAllButton.setEnabled(true);
                                                uploadAllButton.requestFocusInWindow();
                                            });
                                        }
                                    },
                                    3000
                            );
                            return;
                        }
                        if (log.getLinkToUploadedFirstLines() != null) {
                            successCounter++;
                        }
                        if (successCounter == uploadBatch.size()) {
                            break outerLoop;
                        }
                        try {
                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }
                generateMsg(finalModlistDiffFuture, uploadBatch);
            }

            String warningMsg = CrashAssistantConfig.get("generated_message.warning_after_upload_all_button_press", true);
            ClipboardUtils.copy(generatedMsg);
            SwingEDT.runAndWait(() -> {
                uploadAllMessageCopied = true;
                updateUploadArrayBrowserButtonVisibility();
            });
            int buttonHighLightTime = 3000;
            if (!uploadAllButtonWarningShown && !warningMsg.isEmpty()) {
                buttonHighLightTime = 4500;
                showUploadAllButtonWarning(warningMsg);
                ClipboardUtils.copy(generatedMsg);
            }
            int finalButtonHighLightTime = buttonHighLightTime;
            SwingEDT.runAndWait(() -> {
                uploadAllButton.setText(LanguageProvider.get("gui.copied"));
                CrashAssistantGUI.highlightButton(uploadAllButton, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_success_color"), new Color(100, 255, 100)), finalButtonHighLightTime - 400);
            });
            new Timer().schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            SwingEDT.runAndWait(() -> {
                                uploadAllButton.setText(LanguageProvider.get("gui.upload_all_finished_button"));
                                uploadAllButton.setEnabled(true);
                                uploadAllButton.requestFocusInWindow();
                            });
                        }
                    },
                    finalButtonHighLightTime
            );
        }).start();
    }

    public void generateMsg(CompletableFuture<String> modlistDiffFuture) {
        generateMsg(modlistDiffFuture, new ArrayList<>(fileListPanel.getFilePanelList()));
    }

    private void generateMsg(CompletableFuture<String> modlistDiffFuture, List<FilePanel> uploadBatch) {
        List<String> logs = new ArrayList<>();

        boolean kubeJSPosted = false;
        List<Log> kubeJSPanelList = new ArrayList<>();
        for (FilePanel panel : uploadBatch) {
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

        for (FilePanel panel : uploadBatch) {
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
        List<String> copiedAnalysisResults = GeneratedMessage.getAnalysisResults();
        if ((!KnownCrashReasonMessage.getAllMessages().isEmpty() || !copiedAnalysisResults.isEmpty())
                && CrashAssistantConfig.getBoolean("generated_message.put_analysis_result_to_message")) {
            ModListDiffStringBuilder analysis_sb = new ModListDiffStringBuilder();
            HashMap<KnownCrashReason, List<Log>> reasonToLogs = KnownCrashReasonMessage.getUniqueMessages();

            analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_1"), false);
            analysis_sb.append(Integer.toString(reasonToLogs.size() + copiedAnalysisResults.size()), "blue", false);
            analysis_sb.append(LanguageProvider.getMsgLang("msg.found_analysis_2"));

            int scriptedResultsCount = (int) KnownCrashReasonMessage.getAllMessagesSnapshot().stream()
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
            copiedAnalysisResults.forEach(analysis_sb::append);
            String ansiAnalysis = analysis_sb.toAnsi(true).trim();
            if (!ansiAnalysis.isEmpty()) {
                if (!problematicFrame.toString().isEmpty()) analysisResult.append("\n");
                analysisResult.append(ansiAnalysis);
            }
        }

        StringBuilder modListDiffContent = new StringBuilder();
        GeneratedMessageComparison generatedComparison = generatedMessageComparison;
        ModListComparison comparison = generatedComparison == null
                ? null
                : generatedComparison.comparison;
        String comparisonTitle = generatedComparison == null ? null : generatedComparison.title;
        String comparisonPart1 = generatedComparison == null ? null : generatedComparison.part1;
        String comparisonPart2 = generatedComparison == null ? null : generatedComparison.part2;
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")
                && generatedComparison != null
                && comparison == null) {
            ModListDiffStringBuilder noComparisonBuilder = createNoComparisonDiffMessage(
                    comparisonTitle,
                    generatedComparison.noComparisonMessage);
            String noComparisonText = noComparisonBuilder.toText();
            String noComparisonAnsi = noComparisonBuilder.toFormattedString(
                    CrashAssistantConfig.get("generated_message.ansi_block_pattern", false),
                    ModListDiff.getFilePrefix(),
                    comparisonTitle,
                    true
            );

            try {
                if (modlistDiffFuture == null) {
                    modlistDiffFuture = uploadModlistDiff(noComparisonText);
                }
                modlistDiffFuture.get();
            } catch (ExecutionException | InterruptedException | UploadException e) {
                CrashAssistantApp.LOGGER.error("Failed to upload no-comparison modlist message", e);
            }

            modListDiffContent.append("\n");
            modListDiffContent.append(noComparisonAnsi);
        } else if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled") && comparison != null) {
            ModListDiff modListDiff = comparison.createDiff();
            ModListDiffStringBuilder diffStringBuilder = modListDiff.generateDiffMsg(true, comparisonTitle);
            String modlistDiffText = diffStringBuilder.toText();
            String ansiPattern = CrashAssistantConfig.get("generated_message.ansi_block_pattern", false);
            String filePrefix = ModListDiff.getFilePrefix();
            String modListDiffAnsi = diffStringBuilder.toFormattedString(
                    ansiPattern,
                    filePrefix,
                    comparisonTitle,
                    true
            );

            if (modListDiff.isEmpty()) {
                modListDiffContent.append("\n");
                modListDiffContent.append(modListDiffAnsi);
            } else {
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

                    String firstString = formatLinkedComparisonTitle(
                            comparisonPart1,
                            comparisonPart2,
                            link);

                    modListDiffContent.append("\n");
                    modListDiffContent.append(ansiPattern
                            .replace("$PREFIX$", filePrefix)
                            .replace("$HEADER$", firstString)
                            .replace("$CONTENT$", summaryContent));

                } catch (ExecutionException | InterruptedException | UploadException e) {
                    CrashAssistantApp.LOGGER.error("Failed to upload modlist diff message", e);
                    if (modListDiffContent.length() == 0) modListDiffContent.append("\n");
                    modListDiffContent.append(modListDiffAnsi);
                }
            }
        }

        String finalStructure = GeneratedMessage.getCurrentStructure();
        Map<String, String> messageValues = new HashMap<>();
        messageValues.put("HEADER", header);
        messageValues.put("TEXT_UNDER_CRASHED", textUnderCrashed);
        messageValues.put("PREFIX", prefix);
        messageValues.put("LOGS", joinedLogs);
        messageValues.put("PROBLEMATIC_FRAME", problematicFrame.toString());
        messageValues.put("ANALYSIS_RESULT", analysisResult.toString());
        messageValues.put("MODLIST_DIFF", modListDiffContent.toString());
        generatedMsg = GeneratedMessage.renderStructure(finalStructure, messageValues);
        generatedMsgBatch = new ArrayList<>(uploadBatch);
        generatedMsgScriptRevision = GeneratedMessage.getRevision();

        CrashAssistantApp.LOGGER.info("Generated message successfully:\n\n\n" + generatedMsg + "\n\n\n");
    }

    private static String formatLinkedComparisonTitle(String part1, String part2, String link) {
        return CrashAssistantConfig.get("generated_message.modlist_header_pattern", false)
                .replace("$PART1$", part1 == null ? "" : part1)
                .replace("$PART2$", part2 == null ? "" : part2)
                .replace("$LINK$", link);
    }

    private static ModListDiffStringBuilder createNoComparisonDiffMessage(String comparisonTitle,
                                                                           String noComparisonMessage) {
        ModListDiffStringBuilder builder = new ModListDiffStringBuilder();
        builder.append(comparisonTitle == null ? "" : comparisonTitle);
        builder.append(noComparisonMessage == null
                ? LanguageProvider.getMsgLang("msg.modlist_first_launch")
                : noComparisonMessage, "blue");
        return builder;
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

    public static void showUploadAllButtonWarning(String warningMsg) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingEDT.runAndWait(() -> showUploadAllButtonWarning(warningMsg));
            return;
        }
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

    /** One atomic publication unit for upload/message comparison metadata. */
    private static final class GeneratedMessageComparison {
        private final ModListComparison comparison;
        private final String title;
        private final String part1;
        private final String part2;
        private final String noComparisonMessage;

        private GeneratedMessageComparison(ModListComparison comparison,
                                           String title,
                                           String part1,
                                           String part2,
                                           String noComparisonMessage) {
            this.comparison = comparison;
            this.title = title;
            this.part1 = part1;
            this.part2 = part2;
            this.noComparisonMessage = noComparisonMessage;
        }
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
        SwingEDT.runAndWait(() -> {
            if (requestHelpButton != null && hideRequestHelpButton) requestHelpButton.setVisible(false);
        });
    }
}
