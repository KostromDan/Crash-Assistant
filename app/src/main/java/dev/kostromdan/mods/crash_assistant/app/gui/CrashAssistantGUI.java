package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalyser;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogsList;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.app.utils.TerminatedProcessesFinder;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
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

        // Remaining initialization code (unchanged)
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            final long startTime = Instant.now().toEpochMilli();

            @Override
            public void run() {
                if (!ControlPanel.stopMovingToTop) {
                    IntegratedGPUWarning.awaitShown();
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

        IntegratedGPUWarning.awaitShown();

        showCrashAssistantDuplicatedWarning();
        IncompatibleModsWarning.showWarnings(CrashAssistantGUI.frame);
        IntelChipBugWarning.showIfAffected(false);
        new Thread(() -> {
            LogAnalyser.analyseLogs();
            showKnownCrashReasonsWarnings();
        }).start();
    }

    private void addFileMenu() {
        // Initialize menu bar and main menus
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu(LanguageProvider.get("gui.menu.file"));
        JMenu analysisMenu = new JMenu(LanguageProvider.get("gui.menu.analysis"));

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

        // Add menus to menu bar and set to frame
        menuBar.add(fileMenu);
        menuBar.add(analysisMenu);
        frame.setJMenuBar(menuBar);
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
        ControlPanel.stopMovingToTop = true;
        synchronized (KnownCrashReasonMessage.class) {
            try {
                if (PlatformHelp.platform != PlatformHelp.FORGE &&
                        PlatformHelp.platform != PlatformHelp.NEOFORGE) return;
                List<Mod> mods = JarInJarHelper.checkDuplicatedCrashAssistantMod(false);
                if (mods.size() < 2) return;
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
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setText("<html><div " + (wrap ? "" : "style='white-space:nowrap;'") + ">" + text.replaceAll("\n", "<br>") + "</div></html>");

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
        SwingUtilities.invokeLater(() -> {
            for (Log log : LogsList.getLogs()) {
                if (fileListPanel.filePanelList.stream().noneMatch(x -> Objects.equals(x.getLog(), log))) {
                    fileListPanel.addLog(log);
                }
            }
            CrashAssistantGUI.resize();
        });
        LogAnalyser.analyseLogs();
        showKnownCrashReasonsWarnings();
    }

    public static String getTitleCrashedText(boolean forMsg) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        return CrashAssistantApp.crashed_with_report ?
                langFunc.apply("gui.title_crashed_with_report") :
                langFunc.apply("gui.title_crashed_without_report");
    }
}



