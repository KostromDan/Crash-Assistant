package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.*;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.app.utils.TerminatedProcessesFinder;
import dev.kostromdan.mods.crash_assistant.app.gui.PrivacyPolicyDialog;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.MalwareMod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.function.Function;

public class CrashAssistantGUI {
    private static JFrame frame = null;
    public static FileListPanel fileListPanel;
    private static ControlPanel controlPanel;
    private static JPanel labelPanel;
    private static HashSet<JComponent> highlightedButtons = new HashSet<>();
    private static Integer heightWithoutScrollPane = null;


    public CrashAssistantGUI() {
        LanguageProvider.updateLang();
        frame = new JFrame(LanguageProvider.get("gui.window_name"));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                synchronized (TerminatedProcessesFinder.class) {
                    CrashAssistantApp.LOGGER.info("Crash Assistant closed.");
                    System.exit(0);
                }
            }
        });

        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        addFileMenu();

        String titleText = LanguageProvider.get("gui.oops") + getTitleCrashedText(false) + "!";
        JLabel titleLabel = new JLabel(titleText, SwingConstants.LEFT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));

        HashMap<String, String> hrefOptions = new HashMap<>() {{
            put("$CONFIG.text.support_name$", null);
            put("$LANG.gui.upload_all_comment$", null);
        }};

        String firstLinesOfComment = PlatformHelp.isLinkDefault() ?
                LanguageProvider.get("gui.comment_under_title_cant_resolve", hrefOptions) :
                LanguageProvider.get("gui.comment_under_title_pls_report", hrefOptions);

        // Main comment text (excluding screenshot notice)
        String commentText = firstLinesOfComment + "\n" + LanguageProvider.get("gui.comment_under_title", hrefOptions);
        JEditorPane commentPane = getEditorPaneNoMargins(commentText, false);

        labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
        labelPanel.add(titleLabel);
        if (!commentText.isEmpty()) {
            labelPanel.add(commentPane);
        }

        // Screenshot notice in a separate JEditorPane
        if (CrashAssistantConfig.getBoolean("general.show_dont_send_screenshot_of_gui_notice")) {
            String screenshotNoticeText = LanguageProvider.get("gui.comment_under_title_screenshot_notice");
            String screenshotHtml = "<span style='color:red;'><b>" + screenshotNoticeText + "</b></span>";
            JEditorPane screenshotNoticePane = getEditorPaneNoMargins(screenshotHtml, false);

            // Apply the animated border
            if (CrashAssistantConfig.getBoolean("general.screenshot_of_gui_notice_animated_border")) {
                screenshotNoticePane.setBorder(new AnimatedBorder(screenshotNoticePane, Color.RED, false));
            }
            labelPanel.add(screenshotNoticePane);
        }

        frame.add(labelPanel, BorderLayout.NORTH);

        fileListPanel = new FileListPanel();
        frame.add(fileListPanel.getScrollPane(), BorderLayout.CENTER);

        controlPanel = new ControlPanel(fileListPanel);
        frame.add(controlPanel.getPanel(), BorderLayout.SOUTH);

        heightWithoutScrollPane = frame.getPreferredSize().height;

        for (Log log : LogsList.getLogs()) {
            fileListPanel.addLog(log);
        }
        DragAndDrop.enableDragAndDrop(fileListPanel.getScrollPane(), fileListPanel.fileListPanelFilesDragAndDrop);

        resize();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            final long startTime = Instant.now().toEpochMilli();

            @Override
            public void run() {
                if (!ControlPanel.stopMovingToTop) {
                    SwingUtilities.invokeLater(() -> {
                        frame.setAlwaysOnTop(true);
                        frame.toFront();
                        frame.setAlwaysOnTop(false);
                    });
                }
                if (Instant.now().toEpochMilli() - startTime > 5000) {
                    this.cancel();
                }
            }
        }, 0, 50);
        CrashAssistantApp.GUIStartTime = Instant.now().toEpochMilli() - CrashAssistantApp.GUIStartTime;
        CrashAssistantApp.GUIInitialisationFinished = true;
        CrashAssistantApp.LOGGER.info("CrashAssistantGUI took to start: " + CrashAssistantApp.GUIStartTime / 1000f + " seconds.");


        controlPanel.updateModListInfo();
        showCrashAssistantDuplicatedWarning();
        showMalwareModsWarning();
        IncompatibleModsWarning.showWarnings(CrashAssistantGUI.frame);
        IntelChipBugWarning.showIfAffected(false);
        new Thread(() -> {
            LogAnalyser.analyseLogs();
            showKnownCrashReasonsWarnings();
        }).start();
    }

    private static void addFileMenu() {
        // Initialize menu bar and main menus
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu(LanguageProvider.get("gui.menu.file"));
        JMenu analysisMenu = new JMenu(LanguageProvider.get("gui.menu.analysis"));
        JMenu privacyMenu = new JMenu(LanguageProvider.get("gui.menu.privacy"));

        // File menu items

        // Open config file (existing)
        JMenuItem openConfigItem = new JMenuItem(LanguageProvider.get("gui.menu.file.open_config"));
        openConfigItem.addActionListener(e -> {
            try {
                File configFile = new File("config/crash_assistant/config.toml");
                Desktop.getDesktop().open(configFile);
            } catch (IOException ex) {
                CrashAssistantApp.LOGGER.error("Error opening config file", ex);
            }
        });
        fileMenu.add(openConfigItem);

        // Open mods folder
        JMenuItem openModsFolderItem = new JMenuItem(LanguageProvider.get("gui.menu.file.open_mods_folder"));
        openModsFolderItem.addActionListener(e -> {
            try {
                File modsFolder = new File("mods");
                Desktop.getDesktop().open(modsFolder);
            } catch (IOException ex) {
                CrashAssistantApp.LOGGER.error("Error opening mods folder", ex);
            }
        });
        fileMenu.add(openModsFolderItem);

        // Open config folder
        JMenuItem openConfigFolderItem = new JMenuItem(LanguageProvider.get("gui.menu.file.open_config_folder"));
        openConfigFolderItem.addActionListener(e -> {
            try {
                File configFolder = new File("config");
                Desktop.getDesktop().open(configFolder);
            } catch (IOException ex) {
                CrashAssistantApp.LOGGER.error("Error opening config folder", ex);
            }
        });
        fileMenu.add(openConfigFolderItem);

        // Open modpack folder
        JMenuItem openModpackFolderItem = new JMenuItem(LanguageProvider.get("gui.menu.file.open_modpack_folder"));
        openModpackFolderItem.addActionListener(e -> {
            try {
                File modpackFolder = new File(".");
                Desktop.getDesktop().open(modpackFolder);
            } catch (IOException ex) {
                CrashAssistantApp.LOGGER.error("Error opening modpack folder", ex);
            }
        });
        fileMenu.add(openModpackFolderItem);

        // Analysis menu items
        JMenuItem analysisItem = new JMenuItem(LanguageProvider.get("gui.menu.analysis.create_dependencies"));
        analysisItem.addActionListener(e -> CreateDependencies.showCreateAnalysisDialog(frame));
        analysisMenu.add(analysisItem);

        // Privacy menu items
        JMenuItem logsPrivacyItem = new JMenuItem(LanguageProvider.get("gui.menu.privacy.logs_info"));
        logsPrivacyItem.addActionListener(e -> showLogsPrivacyInfo());
        privacyMenu.add(logsPrivacyItem);

        // Reset consent menu item
        JMenuItem resetConsentItem = new JMenuItem(LanguageProvider.get("gui.menu.privacy.reset_consent"));
        resetConsentItem.addActionListener(e -> PrivacyPolicyDialog.resetPrivacyConsent());
        privacyMenu.add(resetConsentItem);

        // Add menus to menu bar and set to frame
        menuBar.add(fileMenu);
        menuBar.add(analysisMenu);
        menuBar.add(privacyMenu);
        frame.setJMenuBar(menuBar);
    }

    private static void showLogsPrivacyInfo() {
        String privacyInfo = LanguageProvider.get("gui.privacy.mclogs_privacy_info", new HashMap<String, String>() {{
            put("$LINK.MCLOGS_PRIVACY_POLICY$", LanguageProvider.get("gui.privacy.privacy_policy"));
        }});
        if (isUploadingToGnome()) {
            privacyInfo = privacyInfo.replace("$GNOMEBOT_PRIVACY_INFO$", LanguageProvider.get("gui.privacy.gnomebot_privacy_info"));
        } else {
            privacyInfo = privacyInfo.replace("$GNOMEBOT_PRIVACY_INFO$", "");
        }

        JOptionPane optionPane = new JOptionPane(
                getEditorPane(privacyInfo, true, 600),
                JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION
        );
        JDialog dialog = optionPane.createDialog(
                frame,
                LanguageProvider.get("gui.privacy.title")
        );
        dialog.setVisible(true);
    }

    public static void resize() {
        frame.setSize(Math.max(Math.max(fileListPanel.getFileListPanel().getPreferredSize().width + 12, controlPanel.getPanel().getPreferredSize().width) + 26, labelPanel.getPreferredSize().width + 20),
                Math.min(heightWithoutScrollPane + fileListPanel.getFileListPanel().getPreferredSize().height + 39, 700));
        frame.setMinimumSize(new Dimension(frame.getSize().width, heightWithoutScrollPane + 73));
    }

    public static synchronized void showKnownCrashReasonsWarnings() {
        ControlPanel.stopMovingToTop = true;
        synchronized (KnownCrashReasonMessage.class) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (KnownCrashReasonMessage crashReason : KnownCrashReasonMessage.getAllMessages()) {
                        if (crashReason.isShownWarn()) continue;
                        if (KnownCrashReason.shownKnownCrashReasons.contains(crashReason.getReason())) continue;
                        HashSet<String> conflictingReasons = crashReason.getReason().getConflictingReasons();
                        if (!conflictingReasons.isEmpty() &&
                                KnownCrashReason.shownKnownCrashReasons.stream()
                                        .anyMatch(x -> conflictingReasons
                                                .contains(x.getClass().getSimpleName()))) {
                            CrashAssistantApp.LOGGER.info("Skipping KnownCrashReason: {}",
                                    crashReason.getReason().getClass().getSimpleName());
                            continue;
                        }

                        KnownCrashReason.shownKnownCrashReasons.add(crashReason.getReason());
                        CrashAssistantApp.LOGGER.info("Showing KnownCrashReason: {}\n{}",
                                crashReason.getReason().getClass().getSimpleName(),
                                crashReason.isCodexMessage() ? crashReason.getMessage() : crashReason.getMessage().split("\n")[0] + "...");
                        crashReason.setShownWarn(true);
                        JOptionPane optionPane = new JOptionPane(
                                CrashAssistantGUI.getEditorPane(crashReason.getMessage(), crashReason.isCodexMessage()),
                                JOptionPane.WARNING_MESSAGE,
                                JOptionPane.DEFAULT_OPTION
                        );
                        JDialog dialog = optionPane.createDialog(
                                frame,
                                crashReason.isCodexMessage() ? LanguageProvider.get("gui.codex_logs_analyser") : LanguageProvider.get("gui.logs_analyser")
                        );
                        dialog.setVisible(true);
                        CrashAssistantApp.LOGGER.info("Shown KnownCrashReason: {}", crashReason.getReason().getClass().getSimpleName());
                    }
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing known crash reasons warnings: ", e);
            }
        }
    }

    public static void showCrashAssistantDuplicatedWarning() {
        synchronized (KnownCrashReasonMessage.class) {
            try {
                if (PlatformHelp.platform != PlatformHelp.FORGE &&
                        PlatformHelp.platform != PlatformHelp.NEOFORGE) return;
                List<Mod> mods = JarInJarHelper.checkDuplicatedCrashAssistantMod(false);
                if (mods.size() < 2) return;
                ControlPanel.stopMovingToTop = true;
                SwingUtilities.invokeAndWait(() -> {
                    JOptionPane optionPane = new JOptionPane(
                            CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.duplicated_mod_warn") +
                                    String.join("\n", mods.stream().map(Mod::getJarName).toList()), false),
                            JOptionPane.WARNING_MESSAGE,
                            JOptionPane.DEFAULT_OPTION
                    );
                    JDialog dialog = optionPane.createDialog(
                            frame,
                            LanguageProvider.get("gui.duplicated_mod")
                    );
                    dialog.setVisible(true);
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing crash assistant duplicated warning: ", e);
            }
        }
    }

    public static void showMalwareModsWarning() {
        synchronized (KnownCrashReasonMessage.class) {
            try {
                Optional<MalwareMod> malwareMod = JarInJarHelper.checkForMalwareMods(false);
                if (!malwareMod.isPresent()) return;
                List<Mod> detectedMods = malwareMod.get().getDetectedMods();
                if (detectedMods.isEmpty()) return;
                ControlPanel.stopMovingToTop = true;
                SwingUtilities.invokeAndWait(() -> {
                    JButton removeButton = new JButton("Remove Malware Mods");
                    Object[] options = {removeButton, "Close"};
                    JOptionPane optionPane = new JOptionPane(
                            CrashAssistantGUI.getEditorPane("<h2>Warning: Malware or malware-like mod detected!</h2>\n" +
                                    "Crash Assistant prevented launch to prevent <strong>potential infection</strong>.\n" +
                                    "Malware mod:\n" +
                                    "<strong>" + String.join("\n", detectedMods.stream().map(Mod::getJarName).toList()) + "</strong>" +
                                    "\n\n" +
                                    "<h4><strong>Why Crash Assistant marked this mod as malware?:</strong></h4>" +
                                    malwareMod.get().getExplainMessage() +
                                    "\n\n" +
                                    "Mods marked as malware may harm your computer or steal your information. It is highly recommended to remove them.", true, 600),
                            JOptionPane.WARNING_MESSAGE,
                            JOptionPane.DEFAULT_OPTION,
                            null,
                            options,
                            options[0]
                    );
                    JDialog dialog = optionPane.createDialog(
                            frame,
                            "Malware Mods Detected"
                    );
                    dialog.setAlwaysOnTop(true);

                    // Add window listener to handle close button
                    dialog.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
                            synchronized (TerminatedProcessesFinder.class) {
                                CrashAssistantApp.LOGGER.info("Malware mods dialog closed with window close button. Exiting with code 0.");
                                System.exit(0);
                            }
                        }
                    });

                    removeButton.addActionListener(e -> {
                        try {
                            dialog.setAlwaysOnTop(false);
                            boolean allDeleted = true;
                            for (Mod mod : detectedMods) {
                                String jarName = mod.getJarName();
                                File modsDir = new File("mods");
                                File modFile = new File(modsDir, jarName);

                                if (modFile.exists()) {
                                    if (modFile.delete()) {
                                        CrashAssistantApp.LOGGER.info("Successfully deleted malware mod: {}", jarName);
                                    } else {
                                        CrashAssistantApp.LOGGER.error("Failed to delete malware mod: {}", jarName);
                                        allDeleted = false;
                                    }
                                } else {
                                    CrashAssistantApp.LOGGER.error("Could not find malware mod file: {}", jarName);
                                    allDeleted = false;
                                }
                            }

                            if (allDeleted) {
                                JOptionPane.showMessageDialog(
                                        frame,
                                        CrashAssistantGUI.getEditorPane("Malware mods have been removed. Please restart your game.", false),
                                        "Malware Mods Removed",
                                        JOptionPane.INFORMATION_MESSAGE
                                );
                                synchronized (TerminatedProcessesFinder.class) {
                                    CrashAssistantApp.LOGGER.info("All malware mods deleted successfully. Exiting with code 0.");
                                    System.exit(0);
                                }
                            } else {
                                JOptionPane.showMessageDialog(
                                        frame,
                                        CrashAssistantGUI.getEditorPane("Some malware mods could not be removed. Please delete them manually from your mods folder.", false),
                                        "Warning",
                                        JOptionPane.WARNING_MESSAGE
                                );
                            }
                        } catch (Exception ex) {
                            CrashAssistantApp.LOGGER.error("Error while removing malware mod: ", ex);
                            JOptionPane.showMessageDialog(
                                    frame,
                                    CrashAssistantGUI.getEditorPane("Failed to remove malware mod: " + ex.getMessage(), false),
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });

                    dialog.setVisible(true);

                    // If we reach here, dialog was closed with the Close button
                    synchronized (TerminatedProcessesFinder.class) {
                        CrashAssistantApp.LOGGER.info("Malware mods dialog closed. Exiting with code 0.");
                        System.exit(0);
                    }
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing malware mod warning: ", e);
            }
        }
    }

    public static void highlightButton(JComponent button, Color color, long time) {
        if (highlightedButtons.contains(button)) {
            return;
        }
        highlightedButtons.add(button);
        Color originalColor = button.getBackground();

        javax.swing.Timer timer = new javax.swing.Timer(400, null);
        final int[] count = {0};
        long startTime = Instant.now().toEpochMilli();
        timer.addActionListener(e -> {
            if (count[0] % 2 == 0) {
                button.setBackground(color);
            } else {
                button.setBackground(originalColor);
            }

            count[0]++;
            if (Instant.now().toEpochMilli() - startTime > time) {
                button.setBackground(originalColor);
                highlightedButtons.remove(button);
                timer.stop();
            }
        });

        timer.start();
    }

    public static HyperlinkListener getHyperlinkListener() {
        return e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String description = e.getDescription();

                JComponent componentToHighlight;
                if ("LANG.gui.upload_all_comment".equals(description)) {
                    componentToHighlight = controlPanel.uploadAllButton;
                } else if ("LANG.gui.file_list_label".equals(description)) {
                    componentToHighlight = fileListPanel.getScrollPane();
                    if (ControlPanel.dialog != null) {
                        ControlPanel.dialog.dispose();
                    }
                } else if ("CONFIG.text.support_name".equals(description)) {
                    componentToHighlight = controlPanel.requestHelpButton;
                } else if ("PRIVACY_POLICY".equals(description)) {
                    showLogsPrivacyInfo();
                    return;
                } else if (e.getURL() != null) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (Exception exception) {
                        CrashAssistantApp.LOGGER.error("Failed to open in link browser: ", exception);
                    }
                    return;
                } else {
                    CrashAssistantApp.LOGGER.error("Unsupported hyperlink event: " + description);
                    return;
                }
                CrashAssistantGUI.highlightButton(componentToHighlight, new Color(100, 100, 255), 3000);
            }
        };
    }

    public static JEditorPane getEditorPane(String text, boolean wrap) {
        return getEditorPane(text, wrap, null);
    }

    public static JEditorPane getEditorPane(String text, boolean wrap, Integer width) {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        StringBuilder html = new StringBuilder();
        html.append("<html>");
        if (width != null) {
            html.append("<body style='width:" + width + "px;'>");
        }
        html.append("<div " + (wrap ? "" : "style='white-space:nowrap;'") + ">" + text.replaceAll("\n", "<br>") + "</div>");
        if (width != null) {
            html.append("</body>");
        }
        html.append("</html>");
        pane.setText(html.toString());

        Font defaultFont = UIManager.getFont("Label.font");
        String bodyRule = "body { font-family: " + defaultFont.getFamily() + "; " +
                "font-size: " + defaultFont.getSize() + "pt; }";
        ((HTMLDocument) pane.getDocument()).getStyleSheet().addRule(bodyRule);

        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBackground(new JButton().getBackground());
        pane.addHyperlinkListener(getHyperlinkListener());
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        return pane;
    }

    public static JEditorPane getEditorPaneNoMargins(String text, boolean wrap) {
        // Call the original getEditorPane method
        JEditorPane pane = getEditorPane(text, wrap);

        // Apply adjustments to remove margins and borders
        pane.setMargin(new Insets(0, 0, 0, 0)); // Remove internal margins
        pane.setBorder(BorderFactory.createEmptyBorder()); // Remove border spacing

        // Ensure HTML content has no internal margins or padding
        String bodyRule = "body { margin: 0; padding: 0; }";
        ((HTMLDocument) pane.getDocument()).getStyleSheet().addRule(bodyRule);

        return pane;
    }

    public static boolean isUploadingToGnome() {
        return Objects.equals(CrashAssistantConfig.get("general.upload_to"), "gnomebot.dev") || PlatformHelp.isLinkDefault();
    }

    public static String getUploadToLink() {
        return isUploadingToGnome() ? "gnomebot.dev" : "mclo.gs";
    }

    public static String transformLink(String link) {
        if (isUploadingToGnome()) {
            String id = link.substring(link.lastIndexOf("/") + 1);
            link = "https://gnomebot.dev/paste/mclogs/" + id;
        }
        return link;
    }

    public static void updateLogsListInGUI() {
        SwingUtilities.invokeLater(CrashAssistantGUI::addMissingLogs);
        LogAnalyser.analyseLogs();
        showKnownCrashReasonsWarnings();
    }

    public static void addMissingLogs() {
        for (Log log : LogsList.getLogs()) {
            if (fileListPanel.filePanelList.stream().noneMatch(x -> Objects.equals(x.getLog(), log))) {
                fileListPanel.addLog(log);
            }
        }
        CrashAssistantGUI.resize();
    }

    public static String getTitleCrashedText(boolean forMsg) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        return CrashAssistantApp.crashed_with_report ?
                langFunc.apply("gui.title_crashed_with_report") :
                langFunc.apply("gui.title_crashed_without_report");
    }
}
