package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.PackageFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.CreateDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.EpicFightDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.JdepsDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.MCreatorModDetectorGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.*;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.app.utils.TerminatedProcessesFinder;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.Lang;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.IncompatibleMod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                CrashAssistantApp.LOGGER.info("Crash Assistant closed.");
                System.exit(0);
            }
        });

        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        addFileMenu();

        String titleText = LanguageProvider.get("gui.oops") + getTitleCrashedText(false) + "!";
        JLabel titleLabel = new JLabel(titleText, SwingConstants.LEFT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));

        HashMap<String, String> hrefOptions = new HashMap<String, String>() {{
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
        if (CrashAssistantConfig.getBoolean("gui_customisation.show_dont_send_screenshot_of_gui_notice")) {
            String screenshotNoticeText = LanguageProvider.get("gui.comment_under_title_screenshot_notice");
            String screenshotHtml = "<span style='color:red;'><b>" + screenshotNoticeText + "</b></span>";
            JEditorPane screenshotNoticePane = getEditorPaneNoMargins(screenshotHtml, false);

            // Apply the animated border
            if (CrashAssistantConfig.getBoolean("gui_customisation.screenshot_of_gui_notice_animated_border")) {
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
        showIncompatibleModsWarning();
        IncompatibleModsWarning.showWarnings(CrashAssistantGUI.frame);
        IntelChipBugWarning.showIfAffected(false);
        new Thread(() -> {
            LogAnalyser.analyseLogs();
            showKnownCrashReasonsWarnings();
        }).start();
    }

    private static void addFileMenu() {

        // Helper to build HTML-based menu items with title and description
        java.util.function.BiFunction<String, String, JMenuItem> makeMenuItem = (titleKey, descKey) -> {
            String title = LanguageProvider.get(titleKey);
            String desc = LanguageProvider.get(descKey);

            // Support multiline descriptions and basic HTML escaping
            java.util.function.Function<String, String> esc = s -> s == null ? "" :
                    s.replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\n", "<br>");

            String html = "<html><b>" + esc.apply(title) + "</b><br>" +
                    "<span style='color:gray; font-size:10px;'>" + esc.apply(desc) + "</span></html>";
            return new JMenuItem(html);
        };
        // Initialize menu bar and main menus
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu(LanguageProvider.get("gui.menu.file"));
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
        boolean analysisMenuEnabled = CrashAssistantConfig.getBoolean("analysis_tools.enabled");
        JMenu analysisMenu = new JMenu(LanguageProvider.get("gui.menu.analysis"));
        if (analysisMenuEnabled) {

            List<String> disabledByConfigTools = CrashAssistantConfig.getBlacklistedAnalysisTools();

            if (!disabledByConfigTools.contains("CreateDependenciesAnalysisGUI")) {
                JMenuItem createAnalysisItem = makeMenuItem.apply("gui.menu.analysis.create_dependencies", "gui.menu.analysis.create_dependencies.desc");
                createAnalysisItem.addActionListener(e -> CreateDependenciesAnalysisGUI.showCreateAnalysisDialog(frame));
                analysisMenu.add(createAnalysisItem);
            }

            if (!disabledByConfigTools.contains("EpicFightDependenciesAnalysisGUI")) {
                JMenuItem epicFightAnalysisItem = makeMenuItem.apply("gui.menu.analysis.epic_fight_addons_compatibility", "gui.menu.analysis.epic_fight_addons_compatibility.desc");
                epicFightAnalysisItem.addActionListener(e -> EpicFightDependenciesAnalysisGUI.showEpicFightAnalysisDialog(frame));
                analysisMenu.add(epicFightAnalysisItem);
            }

            if (!disabledByConfigTools.contains("MCreatorModDetectorGUI")) {
                JMenuItem mcreatorDetectorItem = makeMenuItem.apply("gui.menu.analysis.mcreator_mod_detector", "gui.analysis.mcreator_detector.header");
                mcreatorDetectorItem.addActionListener(e -> MCreatorModDetectorGUI.showMCreatorModDetectorDialog(frame));
                analysisMenu.add(mcreatorDetectorItem);
            }

            if (!disabledByConfigTools.contains("PackageFinderGUI")) {
                JMenuItem packageFinderItem = makeMenuItem.apply("gui.menu.analysis.package_class_finder", "gui.analysis.package_finder.header");
                packageFinderItem.addActionListener(e -> PackageFinderGUI.showPackageFinderDialog(frame));
                analysisMenu.add(packageFinderItem);
            }

            if (!disabledByConfigTools.contains("JdepsDependenciesAnalysisGUI")) {
                JMenuItem jdepsAnalysisItem = makeMenuItem.apply("gui.menu.analysis.jdeps_dependencies_analysis", "gui.analysis.jdeps.header");
                jdepsAnalysisItem.addActionListener(e -> JdepsDependenciesAnalysisGUI.showDialog(frame));
                analysisMenu.add(jdepsAnalysisItem);
            }
        }

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
        if (analysisMenuEnabled) {
            menuBar.add(analysisMenu);
        }
        menuBar.add(privacyMenu);
        frame.setJMenuBar(menuBar);
    }

    private static void showLogsPrivacyInfo() {
        String privacyInfo = Lang.applyPlaceHolders("<h2>$LANG.gui.privacy.crash_assistant_privacy_policy.version_text$ $LANG.gui.privacy.crash_assistant_privacy_policy.version$</h2>$LANG.gui.privacy.crash_assistant_privacy_policy.crash_assistant$ $LANG.gui.privacy.crash_assistant_privacy_policy.mclogs$ $LANG.gui.privacy.crash_assistant_privacy_policy.gnomebot$ $LANG.gui.privacy.crash_assistant_privacy_policy.validity$ $LANG.gui.privacy.crash_assistant_privacy_policy.reset$ $LANG.gui.privacy.crash_assistant_privacy_policy.volume$",
                new HashMap<String, String>() {{
                    put("$LINK.MCLOGS_PRIVACY_POLICY$", LanguageProvider.get("gui.privacy.privacy_policy"));
                    put("$LINK.CRASH_ASSISTANT$", LanguageProvider.get("gui.privacy.mod_description"));
                    put("$LINK.CRASH_ASSISTANT_DISCORD$", "discord");
                    put("$LINK.LAT_DISCORD$", "discord");
                }});

        privacyInfo = privacyInfo.replace("$GNOMEBOT_ENABLED$", Objects.toString(isUploadingToGnome()));

        JEditorPane editorPane = getEditorPane(privacyInfo, true, 600);

        // Create a scroll pane with vertical scrolling only
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(editorPane.getPreferredSize().width, 500));

        // Ensure scroll position starts at the top
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));

        JOptionPane optionPane = new JOptionPane(
                scrollPane,
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
                            CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.duplicated_mod_warn")
                                            .replace("$MODS$", String.join("\n", mods.stream().map(Mod::getJarName).collect(Collectors.toList()))),
                                    false),
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

    public static void showIncompatibleModsWarning() {
        synchronized (KnownCrashReasonMessage.class) {
            try {
                Optional<IncompatibleMod> incompatibleMod = JarInJarHelper.checkForIncompatibleMods(false);
                if (!incompatibleMod.isPresent()) return;
                List<Mod> detectedMods = incompatibleMod.get().getDetectedMods();
                if (detectedMods.isEmpty()) return;
                if (!CrashAssistantConfig.getBoolean("compatibility.enabled")) return;
                ControlPanel.stopMovingToTop = true;
                SwingUtilities.invokeAndWait(() -> {
                    JButton removeIncompatibleButton = new JButton("Close " + detectedMods.get(0).getModId() + " and remove.");
                    JButton removeCrashAssistantButton = new JButton("Close crash_assistant and remove.");
                    Object[] options = {removeIncompatibleButton, removeCrashAssistantButton, "Close"};
                    JOptionPane optionPane = new JOptionPane(
                            CrashAssistantGUI.getEditorPane(
                                    "<h2>Warning: incompatible mod(s) detected!</h2>\n" +
                                            "<strong>" + Boot.crashAssistantModJarName + "</strong>" + " and " +
                                            "<strong>" + String.join(", ", detectedMods.stream().map(Mod::getJarName).collect(Collectors.toList())) + "</strong>" +
                                            " are incompatible.\n" +
                                            "You should remove one them!" +
                                            "<h4><strong>Why did Crash Assistant mark this mod as incompatible?</strong></h4>" +
                                            incompatibleMod.get().getExplainMessage(),
                                    true,
                                    600),
                            JOptionPane.WARNING_MESSAGE,
                            JOptionPane.DEFAULT_OPTION,
                            null,
                            options,
                            options[0]
                    );
                    JDialog dialog = optionPane.createDialog(
                            frame,
                            "Incompatible Mods Detected"
                    );
                    dialog.setAlwaysOnTop(true);

                    // Add window listener to handle close button
                    dialog.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
                            CrashAssistantApp.LOGGER.info("Incompatible mods dialog closed with window close button. Exiting with code 0.");
                            System.exit(0);
                        }
                    });

                    removeIncompatibleButton.addActionListener(e -> {
                        try {
                            dialog.setAlwaysOnTop(false);
                            boolean allDeleted = true;
                            for (Mod mod : detectedMods) {
                                String jarName = mod.getJarName();
                                File modsDir = new File("mods");
                                File modFile = new File(modsDir, jarName);

                                if (modFile.exists()) {
                                    if (modFile.delete()) {
                                        CrashAssistantApp.LOGGER.info("Successfully deleted incompatible mod: {}", jarName);
                                    } else {
                                        boolean destroyAttemptSuccess = false;
                                        ifBlock:
                                        if (!Objects.equals(PlatformHelp.childProcessesPIDs, "UNDEFINED")) {
                                            String[] childProcessesData = PlatformHelp.childProcessesPIDs.split("\\n");
                                            if (childProcessesData.length != 1) break ifBlock;
                                            long childProcessPID = Long.parseLong(childProcessesData[0].split(": ")[0]);
                                            long childProcessStart = Long.parseLong(childProcessesData[0].split(": ")[1]);
                                            if (!ProcessHelper.isProcessAlive(childProcessPID)) break ifBlock;
                                            if (ProcessHelper.getProcessStartTime(childProcessPID) != childProcessStart)
                                                break ifBlock;
                                            ProcessHelper.destroyProcessForcibly(childProcessPID);

                                            long startDeleteTime = System.currentTimeMillis();
                                            while (System.currentTimeMillis() - startDeleteTime < 5000) {
                                                if (modFile.delete()) {
                                                    CrashAssistantApp.LOGGER.info("Successfully deleted incompatible mod after retry: {}", jarName);
                                                    destroyAttemptSuccess = true;
                                                    break;
                                                }
                                                try {
                                                    Thread.sleep(100);
                                                } catch (InterruptedException ignored) {
                                                }
                                            }

                                        }
                                        if (!destroyAttemptSuccess) {
                                            CrashAssistantApp.LOGGER.error("Failed to delete incompatible mod: {}", jarName);
                                            allDeleted = false;
                                        }
                                    }
                                } else {
                                    CrashAssistantApp.LOGGER.error("Could not find incompatible mod file: {}", jarName);
                                    allDeleted = false;
                                }
                            }

                            if (allDeleted) {
                                JOptionPane.showMessageDialog(
                                        frame,
                                        CrashAssistantGUI.getEditorPane("Incompatible mods have been removed. Please restart your game.", false),
                                        "Incompatible Mods Removed",
                                        JOptionPane.INFORMATION_MESSAGE
                                );
                                CrashAssistantApp.LOGGER.info("All incompatible mods deleted successfully. Exiting with code 0.");
                                System.exit(0);
                            } else {
                                JOptionPane.showMessageDialog(
                                        frame,
                                        CrashAssistantGUI.getEditorPane("Some incompatible mods could not be removed. Please delete them manually from your mods folder.", false),
                                        "Warning",
                                        JOptionPane.WARNING_MESSAGE
                                );
                            }
                        } catch (Exception ex) {
                            CrashAssistantApp.LOGGER.error("Error while removing incompatible mod: ", ex);
                            JOptionPane.showMessageDialog(
                                    frame,
                                    CrashAssistantGUI.getEditorPane("Failed to remove incompatible mod: " + ex.getMessage(), false),
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });

                    removeCrashAssistantButton.addActionListener(e -> {
                        try {
                            dialog.setAlwaysOnTop(false);
                            String jarName = Boot.crashAssistantModJarName;
                            File modsDir = new File("mods");
                            File modFile = new File(modsDir, jarName);

                            if (!modFile.exists()) {
                                CrashAssistantApp.LOGGER.error("Could not find Crash Assistant mod file: {}", jarName);
                                JOptionPane.showMessageDialog(
                                        frame,
                                        CrashAssistantGUI.getEditorPane("Could not find Crash Assistant mod file. It may have been moved or renamed.", false),
                                        "Warning",
                                        JOptionPane.WARNING_MESSAGE
                                );
                                return;
                            }

                            Files.delete(modFile.toPath());
                            JOptionPane.showMessageDialog(
                                    frame,
                                    CrashAssistantGUI.getEditorPane("Crash Assistant has been successfully removed from:\n" + modFile.getPath() + "\n\n" +
                                            "Please restart your game.", false),
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                            System.exit(0);
                        } catch (Exception ex) {
                            CrashAssistantApp.LOGGER.error("Error while removing Crash Assistant: ", ex);
                            JOptionPane.showMessageDialog(
                                    frame,
                                    CrashAssistantGUI.getEditorPane("Error while removing Crash Assistant: " + ex.getMessage(), false),
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });
                    frame.setVisible(false);
                    dialog.setVisible(true);

                    // If we reach here, dialog was closed with the Close button
                    synchronized (TerminatedProcessesFinder.class) {
                        CrashAssistantApp.LOGGER.info("Incompatible mods dialog closed. Exiting with code 0.");
                        System.exit(0);
                    }
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing incompatible mod warning: ", e);
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
                        ControlPanel.validateIsDomainTrustedAndOpenInBrowser(e.getURL().toString());
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

    public static JFrame getFrame() {
        return frame;
    }
}
