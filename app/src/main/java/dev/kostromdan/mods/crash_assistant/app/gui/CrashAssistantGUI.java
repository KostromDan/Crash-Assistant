package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.CorruptedConfigFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.CorruptedJarFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.ModsSearcherStandaloneProcess;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.PackageFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.AzureLibDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.CreateDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.EpicFightDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.JdepsDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.ConnectorModDetectorGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.MCreatorModDetectorGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.ModListDiffDialog;
import dev.kostromdan.mods.crash_assistant.app.gui.scripts_ide.ScriptsIDE;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.DuplicatedCrashAssistantMod;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;
import dev.kostromdan.mods.crash_assistant.app.utils.*;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.Lang;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.IncompatibleMod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CrashAssistantGUI {
    private static JFrame frame = null;
    public static FileListPanel fileListPanel = null;
    private static ControlPanel controlPanel;
    private static JPanel labelPanel;
    private static JEditorPane commentPane;
    private static JScrollPane fileListScrollPane;
    private static boolean simpleModeActive;
    private static boolean simpleModeWasDisabled = false;
    private static boolean hideModListInSimpleMode;
    private static final Map<JComponent, OriginalState> highlightedComponents = new ConcurrentHashMap<>();

    public static void bumpWindowToFront(Window window) {
        SwingUtilities.invokeLater(() -> {
            Window currentWindow = window;
            if (currentWindow == null || !currentWindow.isDisplayable() || !currentWindow.isVisible()) return;

            boolean wasAlwaysOnTop = currentWindow.isAlwaysOnTop();
            currentWindow.setAlwaysOnTop(true);
            currentWindow.toFront();
            currentWindow.requestFocus();

            if (!wasAlwaysOnTop) {
                javax.swing.Timer resetAlwaysOnTopTimer = new javax.swing.Timer(1000, e -> {
                    if (currentWindow.isDisplayable()) {
                        currentWindow.setAlwaysOnTop(false);
                    }
                });
                resetAlwaysOnTopTimer.setRepeats(false);
                resetAlwaysOnTopTimer.start();
            }
        });
    }

    public static void bumpMainWindowToFront() {
        bumpWindowToFront(frame);
    }

    private static class OriginalState {
        final Color originalBackground;
        final Object originalStyle;
        volatile int generation = 0;

        OriginalState(JComponent component) {
            this.originalBackground = component.getBackground();
            this.originalStyle = component.getClientProperty("FlatLaf.style");
        }
    }


    /**
     * Helper method to load the image from the specified path.
     *
     * @param path The relative path to the image file.
     * @return A BufferedImage object, or null if loading fails.
     */
    private static BufferedImage loadModpackLogo(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        Path logoPath = Paths.get(path);
        if (!Files.exists(logoPath) || !Files.isRegularFile(logoPath)) {
            CrashAssistantApp.LOGGER.error("Modpack logo not found or is not a file: {}", logoPath.toAbsolutePath());
            return null;
        }
        try {
            return ImageIO.read(logoPath.toFile());
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to load modpack logo from path: {}", logoPath.toAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Helper method to resize a BufferedImage into an ImageIcon while maintaining aspect ratio.
     *
     * @param originalImage The source image.
     * @param maxWidth      The maximum width for the resized image.
     * @param maxHeight     The maximum height for the resized image.
     * @return A resized ImageIcon, or null if the original image is invalid.
     */
    private static ImageIcon resizeLogo(BufferedImage originalImage, int maxWidth, int maxHeight) {
        if (originalImage == null) return null;

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        if (originalWidth <= 0 || originalHeight <= 0) {
            return new ImageIcon(originalImage); // Cannot resize, return as is
        }

        double ratio = Math.min((double) maxWidth / originalWidth, (double) maxHeight / originalHeight);

        int newWidth = (int) (originalWidth * ratio);
        int newHeight = (int) (originalHeight * ratio);

        if (newWidth <= 0 || newHeight <= 0) { // Check for invalid dimensions
            return new ImageIcon(originalImage);
        }

        Image resizedImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImage);
    }

    /**
     * Returns true if the provided path looks like a GIF file.
     */
    private static boolean isGifPath(String path) {
        if (path == null) return false;
        String lower = path.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".gif");
    }

    /**
     * Loads an animated GIF as an ImageIcon so the animation is preserved.
     * Avoids ImageIO for GIFs (which decodes only the first frame).
     */
    private static ImageIcon loadAnimatedGifIcon(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        Path logoPath = Paths.get(path);
        if (!Files.exists(logoPath) || !Files.isRegularFile(logoPath)) {
            CrashAssistantApp.LOGGER.error("Modpack logo not found or is not a file: {}", logoPath.toAbsolutePath());
            return null;
        }
        try {
            // ImageIcon preserves GIF animation frames.
            return new ImageIcon(logoPath.toAbsolutePath().toString());
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to load animated GIF modpack logo from path: {}", logoPath.toAbsolutePath(), e);
            return null;
        }
    }

    /**
     * An Icon that draws the underlying (potentially animated) Image scaled to fit within
     * the given maxWidth and maxHeight while maintaining aspect ratio.
     * Because we draw with the component as the ImageObserver, animated GIFs keep animating.
     */
    private static final class ScaledImageIcon extends ImageIcon {
        private final int maxWidth;
        private final int maxHeight;

        ScaledImageIcon(ImageIcon delegate, int maxWidth, int maxHeight) {
            super(delegate.getImage());
            this.maxWidth = Math.max(1, maxWidth);
            this.maxHeight = Math.max(1, maxHeight);
        }

        private Dimension getScaledSize() {
            int w = super.getIconWidth();
            int h = super.getIconHeight();
            if (w <= 0 || h <= 0) {
                // Fallback before dimensions are known; use the provided max bounds.
                return new Dimension(maxWidth, maxHeight);
            }
            double ratio = Math.min((double) maxWidth / w, (double) maxHeight / h);
            int newW = Math.max(1, (int) Math.round(w * ratio));
            int newH = Math.max(1, (int) Math.round(h * ratio));
            return new Dimension(newW, newH);
        }

        @Override
        public int getIconWidth() {
            return getScaledSize().width;
        }

        @Override
        public int getIconHeight() {
            return getScaledSize().height;
        }

        @Override
        public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
            Image image = getImage();
            if (image == null) return;

            Dimension d = getScaledSize();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Use the component as ImageObserver so GIF frames trigger repaints (animation).
                g2.drawImage(image, x, y, d.width, d.height, c);
            } finally {
                g2.dispose();
            }
        }
    }


    public CrashAssistantGUI() {
        ThemeUtils.ensureThemesApplied();
        LanguageProvider.updateLang();
        frame = new JFrame(LanguageProvider.get("gui.window_name"));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                CrashAssistantApp.LOGGER.info("Crash Assistant closed.");
                try {
                    FileUtils.deleteDirectory(ModListDiffDialog.tmpDownloadsFolder.toFile());
                } catch (Throwable ex) {
                    CrashAssistantApp.LOGGER.error("Failed to delete temporary downloads folder.", ex);
                }
                System.exit(0);
            }
        });

        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        setUpIcon(frame);
        addFileMenu();

        // --- Configuration reading ---
        String logoPath = CrashAssistantConfig.get("gui_customisation.modpack_logo_path");
        boolean largeLogoMode = CrashAssistantConfig.getBoolean("gui_customisation.modpack_logo_large_mode");
        int modpackLogoHeightLimit = CrashAssistantConfig.getInteger("gui_customisation.limit_modpack_logo_height");

        // GIF support: choose the correct loader based on extension
        final boolean isGif = isGifPath(logoPath);
        BufferedImage logoImage = isGif ? null : loadModpackLogo(logoPath);             // static images (PNG/JPG/etc.)
        ImageIcon animatedLogoIcon = isGif ? loadAnimatedGifIcon(logoPath) : null;      // animated GIFs

        boolean showScreenshotNotice = CrashAssistantConfig.getBoolean("gui_customisation.show_dont_send_screenshot_of_gui_notice");

        // --- Component Creation ---
        String titleText = getTitleCrashedText(false);
        JLabel titleLabel = new JLabel(titleText, SwingConstants.LEFT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));

        String commentText = updateCommentText();

        String screenshotNoticeText = LanguageProvider.get("gui.comment_under_title_screenshot_notice");
        Color textColor = ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.screenshot_of_gui_notice_text_color"), Color.RED);
        String hexColor = String.format("#%02x%02x%02x", textColor.getRed(), textColor.getGreen(), textColor.getBlue());
        String screenshotHtml = "<span style='color:" + hexColor + ";'><b>" + screenshotNoticeText + "</b></span>";
        JEditorPane screenshotNoticePane = getEditorPaneNoMargins(screenshotHtml, false);
        if (showScreenshotNotice && CrashAssistantConfig.getBoolean("gui_customisation.screenshot_of_gui_notice_animated_border")) {
            Color borderColor = ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.screenshot_of_gui_notice_animated_border_color"), Color.RED);
            screenshotNoticePane.setBorder(new AnimatedBorder(screenshotNoticePane, borderColor, false));
        }

        // --- Panel Construction ---
        labelPanel = new JPanel(new BorderLayout()); // Main container for the top section
        labelPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 3, 5));

        JPanel mainTextPanel = new JPanel();
        mainTextPanel.setLayout(new BoxLayout(mainTextPanel, BoxLayout.Y_AXIS));
        mainTextPanel.setOpaque(false);
        mainTextPanel.add(titleLabel);
        if (!commentText.isEmpty()) {
            mainTextPanel.add(commentPane);
        }

        JLabel modpackLogoLabel = new JLabel();

        // --- Layout Logic ---
        if (logoImage == null && animatedLogoIcon == null) {
            // Case 1: No Logo
            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.add(mainTextPanel);
            if (showScreenshotNotice) {
                contentPanel.add(Box.createVerticalStrut(3));
                contentPanel.add(screenshotNoticePane);
            }
            labelPanel.add(contentPanel, BorderLayout.CENTER);
        } else {
            if (largeLogoMode) {
                // Case 2: Large Logo Mode
                JPanel leftColumn = new JPanel();
                leftColumn.setOpaque(false);
                leftColumn.setLayout(new BoxLayout(leftColumn, BoxLayout.Y_AXIS));
                leftColumn.add(mainTextPanel);
                if (showScreenshotNotice) {
                    leftColumn.add(Box.createVerticalStrut(3));
                    leftColumn.add(screenshotNoticePane);
                }
                labelPanel.add(leftColumn, BorderLayout.CENTER);

                // Determine height for the logo to match the text block
                int textHeight = leftColumn.getPreferredSize().height;

                int maxW = 500;
                int maxH = (modpackLogoHeightLimit != -1) ? Math.min(modpackLogoHeightLimit, textHeight) : textHeight;

                if (animatedLogoIcon != null) {
                    modpackLogoLabel.setIcon(new ScaledImageIcon(animatedLogoIcon, maxW, maxH));
                } else {
                    modpackLogoLabel.setIcon(resizeLogo(logoImage, maxW, maxH));
                }

                modpackLogoLabel.setVerticalAlignment(SwingConstants.CENTER);
                modpackLogoLabel.setHorizontalAlignment(SwingConstants.CENTER);

                JPanel logoWrapper = new JPanel();
                logoWrapper.setLayout(new BoxLayout(logoWrapper, BoxLayout.Y_AXIS));
                logoWrapper.setOpaque(false);
                logoWrapper.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 0));
                logoWrapper.add(Box.createVerticalGlue());
                logoWrapper.add(modpackLogoLabel);
                logoWrapper.add(Box.createVerticalGlue());

                JPanel logoContainer = new JPanel(new BorderLayout());
                logoContainer.setOpaque(false);
                logoContainer.add(logoWrapper, CrashAssistantConfig.getBoolean("gui_customisation.modpack_logo_aligned_center") ? BorderLayout.CENTER : BorderLayout.NORTH);

                labelPanel.add(logoContainer, BorderLayout.EAST);

            } else {
                // Case 3: Small Logo Mode
                JPanel topRowPanel = new JPanel(new BorderLayout(5, 0));
                topRowPanel.add(mainTextPanel, BorderLayout.CENTER);

                int textHeight = mainTextPanel.getPreferredSize().height;
                int maxW = 500;
                int maxH = (modpackLogoHeightLimit != -1) ? Math.min(modpackLogoHeightLimit, textHeight - 3) : (textHeight - 3);

                if (animatedLogoIcon != null) {
                    modpackLogoLabel.setIcon(new ScaledImageIcon(animatedLogoIcon, maxW, maxH));
                } else {
                    modpackLogoLabel.setIcon(resizeLogo(logoImage, maxW, maxH));
                }

                JPanel logoWrapper = new JPanel(new GridBagLayout());
                logoWrapper.setOpaque(false);
                GridBagConstraints logoGbc = new GridBagConstraints();
                logoGbc.anchor = GridBagConstraints.CENTER;
                logoWrapper.add(modpackLogoLabel, logoGbc);
                JPanel logoContainer = new JPanel(new BorderLayout());
                logoContainer.setOpaque(false);
                boolean centerAligned = CrashAssistantConfig.getBoolean("gui_customisation.modpack_logo_aligned_center");
                logoContainer.add(logoWrapper, centerAligned ? BorderLayout.CENTER : BorderLayout.NORTH);
                logoContainer.setBorder(BorderFactory.createEmptyBorder(centerAligned ? 0 : 1, 0, 0, 0));
                topRowPanel.add(logoContainer, BorderLayout.EAST);

                JPanel contentPanel = new JPanel(new GridBagLayout());
                GridBagConstraints gbc = new GridBagConstraints();

                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.anchor = GridBagConstraints.WEST;
                contentPanel.add(topRowPanel, gbc);

                if (showScreenshotNotice) {
                    gbc.gridy = 1;
                    gbc.insets = new Insets(2, 0, 0, 0);
                    contentPanel.add(screenshotNoticePane, gbc);
                }
                labelPanel.add(contentPanel, BorderLayout.CENTER);
            }
        }

        frame.add(labelPanel, BorderLayout.NORTH);

        boolean preventForModpackCreators = CrashAssistantConfig.getBoolean("simple_mode.prevent_for_modpack_creators");
        boolean isModpackCreator = ModListDiff.isModpackCreator();
        boolean simpleModeAllowed = CrashAssistantConfig.getBoolean("simple_mode.enabled") && !(preventForModpackCreators && isModpackCreator);
        boolean alwaysShowLogs = isAlwaysShowLogsEnabled();
        hideModListInSimpleMode = CrashAssistantConfig.getBoolean("simple_mode.hide_modlist_section");

        fileListPanel = new FileListPanel();
        fileListScrollPane = fileListPanel.getScrollPane();
        frame.add(fileListScrollPane, BorderLayout.CENTER);

        simpleModeActive = simpleModeAllowed && !alwaysShowLogs && !simpleModeWasDisabled;
        controlPanel = new ControlPanel(fileListPanel, simpleModeActive, CrashAssistantGUI::handleShowLogsButtonClick);
        frame.add(controlPanel.getPanel(), BorderLayout.SOUTH);
        updateSimpleModeVisibility();

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
        showTooManyChangesWarning();
        IntelChipBugWarning.showIfAffected(false);
        showEarlyIntegratedGPUWarning();
        new Thread(() -> {
            LogAnalyser.analyseLogs();
            showKnownCrashReasonsWarnings();
            showPiracyWarning();
        }).start();
    }

    public static void setUpIcon(Window window) {
        try {
            java.io.InputStream iconStream = JarInJarHelper.class.getResourceAsStream("/crash_assistant_ico.png");
            if (iconStream != null) {
                BufferedImage iconImage = ImageIO.read(iconStream);
                window.setIconImage(iconImage);
                iconStream.close();
            } else {
                CrashAssistantApp.LOGGER.warn("Could not find crash_assistant_logo.png in jar root");
            }
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to load window icon", e);
        }
    }

    public static String updateCommentText() {
        HashMap<String, String> hrefOptions = new HashMap<String, String>() {{
            put("$CONFIG.text.support_name$", null);
            put("$LANG.gui.upload_all_comment$", null);
            put("$LANG.gui.dragging_and_dropping$", null);
        }};
        String formulationType = CrashAssistantConfig.get("general.formulation_type");
        String suffix = formulationType.equalsIgnoreCase("GITHUB") ? ".github" : "";
        boolean isPiracy = UUIDUtils.status == UUIDCheckStatus.PIRACY_OR_OFFLINE && (PlatformHelp.isLinkDefault() || CrashAssistantConfig.getBoolean("piracy.enabled"));
        if (isPiracy) {
            suffix = ".piracy";
        }

        String firstLinesOfComment = PlatformHelp.isLinkDefault() || isPiracy ?
                LanguageProvider.get("gui.comment_under_title_cant_resolve" + suffix, hrefOptions) :
                LanguageProvider.get("gui.comment_under_title_pls_report" + suffix, hrefOptions);

        String commentText = "<div style='margin-left: 5px;'>" + firstLinesOfComment + "\n" + LanguageProvider.get("gui.comment_under_title" + suffix, hrefOptions) + "</div>";
        JEditorPane pane = getEditorPaneNoMargins(commentText, false);
        if (commentPane == null) {
            commentPane = pane;
        } else {
            commentPane.setText(pane.getText());
            commentPane.revalidate();
            commentPane.repaint();
        }
        return commentText;
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

        // Open mods folder
        JMenuItem openModsFolderItem = new JMenuItem(LanguageProvider.get("gui.menu.file.open_mods_folder"));
        openModsFolderItem.addActionListener(e -> {
            try {
                File modsFolder = ModListUtils.MODS_FOLDER.toFile();
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

        // Open Scripts IDE
        if (CrashAssistantConfig.getBoolean("scripts.enabled") && CrashAssistantConfig.getBoolean("scripts.ide_enabled") && (PlatformHelp.isLinkDefault() || ModListDiff.isModpackCreator())) {
            JMenuItem scriptsIdeItem = new JMenuItem("Scripts IDE");
            scriptsIdeItem.addActionListener(e -> ScriptsIDE.main(null));
            fileMenu.add(scriptsIdeItem);
        }

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

            if (!disabledByConfigTools.contains("AzureLibDependenciesAnalysisGUI")) {
                JMenuItem epicFightAnalysisItem = makeMenuItem.apply("gui.menu.analysis.azure_lib_addons_compatibility", "gui.menu.analysis.azure_lib_addons_compatibility.desc");
                epicFightAnalysisItem.addActionListener(e -> AzureLibDependenciesAnalysisGUI.showAzureLibAnalysisDialog(frame));
                analysisMenu.add(epicFightAnalysisItem);
            }

            if (!disabledByConfigTools.contains("MCreatorModDetectorGUI")) {
                JMenuItem mcreatorDetectorItem = makeMenuItem.apply("gui.menu.analysis.mcreator_mod_detector", "gui.analysis.mcreator_detector.header");
                mcreatorDetectorItem.addActionListener(e -> MCreatorModDetectorGUI.showMCreatorModDetectorDialog(frame));
                analysisMenu.add(mcreatorDetectorItem);
            }

            if (!disabledByConfigTools.contains("ConnectorModDetectorGUI") && PlatformHelp.isForgeBased()) {
                JMenuItem connectorDetectorItem = makeMenuItem.apply("gui.menu.analysis.connector_mod_detector", "gui.analysis.connector_detector.header");
                connectorDetectorItem.addActionListener(e -> ConnectorModDetectorGUI.showConnectorModDetectorDialog(frame));
                analysisMenu.add(connectorDetectorItem);
            }

            if (!disabledByConfigTools.contains("ModsSearcherAnalysisGUI")) {
                JMenuItem modsSearcherItem = makeMenuItem.apply("gui.menu.analysis.mods_searcher", "gui.menu.analysis.mods_searcher.desc");
                modsSearcherItem.addActionListener(e -> ModsSearcherStandaloneProcess.launch(frame));
                analysisMenu.add(modsSearcherItem);
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

            if (!disabledByConfigTools.contains("CorruptedJarFinderGUI")) {
                JMenuItem corruptedJarFinderItem = makeMenuItem.apply("gui.menu.analysis.corrupted_jar_finder", "gui.menu.analysis.corrupted_jar_finder.desc");
                corruptedJarFinderItem.addActionListener(e -> CorruptedJarFinderGUI.showDialog(frame));
                analysisMenu.add(corruptedJarFinderItem);
            }

            if (!disabledByConfigTools.contains("CorruptedConfigFinderGUI")) {
                JMenuItem corruptedConfigFinderItem = makeMenuItem.apply("gui.menu.analysis.corrupted_config_finder", "gui.menu.analysis.corrupted_config_finder.desc");
                corruptedConfigFinderItem.addActionListener(e -> CorruptedConfigFinderGUI.showDialog(frame));
                analysisMenu.add(corruptedConfigFinderItem);
            }
        }

        // Privacy menu items
        JMenuItem logsPrivacyItem = new JMenuItem(LanguageProvider.get("gui.menu.privacy.logs_info"));
        logsPrivacyItem.addActionListener(e -> showLogsPrivacyInfo());
        privacyMenu.add(logsPrivacyItem);

        // Manage logs menu item
        JMenuItem manageLogsItem = new JMenuItem(LanguageProvider.get("gui.menu.privacy.manage_logs"));
        manageLogsItem.addActionListener(e -> new LogDeletionDialog(frame).setVisible(true));
        privacyMenu.add(manageLogsItem);

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

    private static boolean isAlwaysShowLogsEnabled() {
        return Objects.equals(CrashAssistantLocalConfig.get("gui.simple_mode.always_show_logs"), true);
    }

    private static boolean isSkipPromptEnabled() {
        return Objects.equals(CrashAssistantLocalConfig.get("gui.simple_mode.skip_prompt"), true);
    }

    private static void updateSimpleModeVisibility() {
        if (fileListScrollPane == null) return;
        fileListScrollPane.setVisible(!simpleModeActive);
        if (controlPanel != null) {
            controlPanel.setSimpleModeButtonVisible(simpleModeActive);
            boolean showModList = controlPanel.wasModListInitiallyVisible() && (!simpleModeActive || !hideModListInSimpleMode);
            controlPanel.setModListSectionVisible(showModList);
        }
        resize();
    }

    public static void showLogsAndDisableSimpleMode() {
        simpleModeActive = false;
        simpleModeWasDisabled = true;
        updateSimpleModeVisibility();
    }

    private static void handleShowLogsButtonClick() {
        if (!simpleModeActive) {
            return;
        }

        showLogsAndDisableSimpleMode();

        if (isSkipPromptEnabled()) {
            return;
        }

        JCheckBox dontAskAgain = new JCheckBox(LanguageProvider.get("gui.simple_mode.prompt_dont_ask"));
        JPanel messagePanel = new JPanel(new BorderLayout(0, 8));
        JEditorPane messagePane = getEditorPane(LanguageProvider.get("gui.simple_mode.prompt_question"), false);
        messagePanel.add(messagePane, BorderLayout.CENTER);
        messagePanel.add(dontAskAgain, BorderLayout.SOUTH);

        Object[] options = new Object[]{
                LanguageProvider.get("gui.simple_mode.prompt_yes"),
                LanguageProvider.get("gui.simple_mode.prompt_no")
        };
        int choice = JOptionPane.showOptionDialog(
                frame,
                messagePanel,
                LanguageProvider.get("gui.simple_mode.prompt_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        boolean treatedAsNo = choice == JOptionPane.NO_OPTION || choice == JOptionPane.CLOSED_OPTION;
        boolean treatAsYes = choice == JOptionPane.YES_OPTION;

        if (treatAsYes) {
            CrashAssistantLocalConfig.set("gui.simple_mode.skip_prompt", true);
            CrashAssistantLocalConfig.set("gui.simple_mode.always_show_logs", true);
        } else if (dontAskAgain.isSelected()) {
            CrashAssistantLocalConfig.set("gui.simple_mode.skip_prompt", true);
        }

        showLogsAndDisableSimpleMode();
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
        if (frame == null || fileListPanel == null) return;
        int old = fileListPanel.getScrollPane().getVerticalScrollBarPolicy();
        fileListPanel.getScrollPane().setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        frame.pack();
        frame.setSize(frame.getPreferredSize().width, Math.min(frame.getPreferredSize().height, 700));
        frame.setMinimumSize(new Dimension(frame.getSize().width, frame.getSize().height));
        fileListPanel.getScrollPane().setVerticalScrollBarPolicy(old);
        frame.repaint();
    }

    public static String getCrashReasonsDialogName(KnownCrashReasonMessage crashReasonMessage) {
        if (crashReasonMessage.isCodexMessage()) {
            return LanguageProvider.get("gui.codex_logs_analyzer");
        } else if (StartupWarningViewer.isStartupWarning) {
            return LanguageProvider.get("gui.startup_warning_dialog_title");
        }
        return LanguageProvider.get("gui.logs_analyzer");

    }

    public static synchronized void showKnownCrashReasonsWarnings() {
        ControlPanel.stopMovingToTop = true;
        synchronized (KnownCrashReasonMessage.class) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    boolean isHeadless = frame == null;
                    if (isHeadless) {
                        frame = new JFrame(LanguageProvider.get("gui.window_name"));
                        frame.setUndecorated(true);
                        frame.setBackground(new Color(0, 0, 0, 0));
                        frame.setSize(0, 0);
                        frame.setLocationRelativeTo(null);
                        frame.setAlwaysOnTop(true);
                        setUpIcon(frame);
                        frame.setVisible(true);
                    }
                    try {
                        for (KnownCrashReasonMessage crashReasonMessage : KnownCrashReasonMessage.getAllMessages()) {
                            if (crashReasonMessage.isShownWarn()) continue;
                            KnownCrashReason crashReason = crashReasonMessage.getReason();
                            if (CrashAssistantConfig.getBlacklistedAnalysis().contains(crashReason.getClass().getSimpleName()))
                                continue;
                            if (KnownCrashReason.shownKnownCrashReasons.contains(crashReason)) continue;
                            HashSet<String> conflictingReasons = crashReason.getConflictingReasons();
                            if (!conflictingReasons.isEmpty() &&
                                    KnownCrashReason.shownKnownCrashReasons.stream()
                                            .anyMatch(x -> conflictingReasons
                                                    .contains(x.getClass().getSimpleName()))) {
                                CrashAssistantApp.LOGGER.info("Skipping KnownCrashReason: {}",
                                        crashReason.getClass().getSimpleName());
                                continue;
                            }

                            KnownCrashReason.shownKnownCrashReasons.add(crashReason);
                            CrashAssistantApp.LOGGER.info("Showing KnownCrashReason: {}\n{}",
                                    crashReason.getClass().getSimpleName(),
                                    "\n \n" + HtmlToMarkdown.convert(crashReasonMessage.getMessage()) + "\n \n");
                            crashReasonMessage.setShownWarn(true);

                            boolean isFromScripts = crashReason instanceof ScriptedAnalysis;

                            JEditorPane messagePane = CrashAssistantGUI.getEditorPane(crashReasonMessage.getMessage(), crashReasonMessage.isCodexMessage() || isFromScripts, isFromScripts ? 600 : null);

                            LinkedHashMap<String, Consumer<JDialog>> autoFixButtons = crashReason.getAutoFixButtons();

                            JButton okButton = new JButton(LanguageProvider.get("gui.ok"));
                            String reasonClassName = crashReason.getClass().getSimpleName();
                            String configKey = "shown_reasons." + reasonClassName;

                            String dontShowAgainKey = crashReason.getDontShowAgainKey();
                            if (dontShowAgainKey != null && Objects.equals(CrashAssistantLocalConfig.get(dontShowAgainKey), true)) {
                                CrashAssistantApp.LOGGER.info("Skipping KnownCrashReason: {}, due to dontShowAgainKey: {}", crashReason.getClass().getSimpleName(), dontShowAgainKey);
                                continue;
                            }
                            boolean alreadyShown = Objects.equals(CrashAssistantLocalConfig.get(configKey), true);

                            JDialog dialog;
                            JCheckBox dontShowBoxRef = null;
                            if (dontShowAgainKey != null) {
                                String text = crashReason.getDontShowAgainCheckboxText();
                                dontShowBoxRef = new JCheckBox(text != null ? text : LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
                                String finalDontShowAgainKey = dontShowAgainKey;
                                JCheckBox finalBox = dontShowBoxRef;
                                dontShowBoxRef.addActionListener(e -> CrashAssistantLocalConfig.set(finalDontShowAgainKey, finalBox.isSelected()));
                            }

                            if (!autoFixButtons.isEmpty()) {
                                JPanel autoFixPanel = new JPanel(new GridBagLayout());
                                GridBagConstraints gbc = new GridBagConstraints();
                                gbc.fill = GridBagConstraints.HORIZONTAL;
                                gbc.weightx = 1.0;
                                gbc.gridy = 0;


                                List<JButton> buttons = new ArrayList<>();
                                List<Consumer<JDialog>> actions = new ArrayList<>();

                                for (Map.Entry<String, Consumer<JDialog>> entry : autoFixButtons.entrySet()) {
                                    JButton autoFixButton = new JButton(entry.getKey());
                                    autoFixButton.setFont(autoFixButton.getFont().deriveFont(Font.BOLD,
                                            CrashAssistantConfig.getInteger("gui_customisation.auto_fix_button_font_size")));
                                    autoFixButton.setForeground(
                                            ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.auto_fix_button_foreground_color"),
                                                    autoFixButton.getForeground()));
                                    gbc.insets = new Insets(0, 0, 5, 0);
                                    autoFixPanel.add(autoFixButton, gbc);
                                    gbc.gridy++;
                                    buttons.add(autoFixButton);
                                    actions.add(entry.getValue());
                                }

                                JPanel southContainer = new JPanel(new BorderLayout(0, 10));
                                southContainer.add(autoFixPanel, BorderLayout.CENTER);

                                JPanel bottomRow = new JPanel(new BorderLayout());
                                if (dontShowBoxRef != null) {
                                    bottomRow.add(dontShowBoxRef, BorderLayout.WEST);
                                }
                                JPanel okPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                                okPanel.add(okButton);
                                bottomRow.add(okPanel, BorderLayout.CENTER);
                                southContainer.add(bottomRow, BorderLayout.SOUTH);

                                JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
                                mainPanel.add(messagePane, BorderLayout.CENTER);
                                mainPanel.add(southContainer, BorderLayout.SOUTH);

                                JOptionPane optionPane = new JOptionPane(
                                        mainPanel,
                                        JOptionPane.WARNING_MESSAGE,
                                        JOptionPane.DEFAULT_OPTION,
                                        null,
                                        new Object[]{},
                                        null
                                );

                                dialog = optionPane.createDialog(
                                        ScriptsIDE.isIdeRunning() ? null : frame,
                                        getCrashReasonsDialogName(crashReasonMessage)
                                );


                                for (int i = 0; i < buttons.size(); i++) {
                                    JButton button = buttons.get(i);
                                    Consumer<JDialog> action = actions.get(i);
                                    JDialog finalDialog = dialog;
                                    button.addActionListener(e -> action.accept(finalDialog));
                                }
                            } else {
                                JPanel panel = new JPanel(new BorderLayout(10, 10));
                                panel.add(messagePane, BorderLayout.CENTER);

                                JPanel bottomRow = new JPanel(new BorderLayout());
                                if (dontShowBoxRef != null) {
                                    bottomRow.add(dontShowBoxRef, BorderLayout.WEST);
                                }
                                JPanel okPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                                okPanel.add(okButton);
                                bottomRow.add(okPanel, BorderLayout.CENTER);
                                panel.add(bottomRow, BorderLayout.SOUTH);

                                JOptionPane optionPane = new JOptionPane(
                                        panel,
                                        JOptionPane.WARNING_MESSAGE,
                                        JOptionPane.DEFAULT_OPTION,
                                        null,
                                        new Object[]{},
                                        null
                                );
                                dialog = optionPane.createDialog(
                                        ScriptsIDE.isIdeRunning() ? null : frame,
                                        getCrashReasonsDialogName(crashReasonMessage)
                                );
                            }

                            okButton.addActionListener(e -> dialog.dispose());


                            if (!alreadyShown) {
                                int delay = crashReason.getOkDelay();
                                if (delay == -1) {
                                    delay = CrashAssistantConfig.getInteger("analysis.first_show_delay");
                                }

                                if (delay > 0) {
                                    okButton.setEnabled(false);
                                    if (dontShowBoxRef != null) dontShowBoxRef.setEnabled(false);
                                    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

                                    final int[] secondsLeft = {delay};
                                    final int initialDelay = delay;
                                    okButton.setText(LanguageProvider.get("gui.ok") + " (" + secondsLeft[0] + ")");
                                    javax.swing.Timer timer = new javax.swing.Timer(1000, null);
                                    JCheckBox finalDontShowBox = dontShowBoxRef;
                                    JDialog finalDialog1 = dialog;
                                    timer.addActionListener(e -> {
                                        secondsLeft[0]--;
                                        int elapsed = initialDelay - secondsLeft[0];
                                        if (elapsed >= 5 && finalDialog1.getDefaultCloseOperation() == JDialog.DO_NOTHING_ON_CLOSE) {
                                            finalDialog1.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                                        }
                                        if (secondsLeft[0] <= 0) {
                                            okButton.setText(LanguageProvider.get("gui.ok"));
                                            okButton.setEnabled(true);
                                            if (finalDontShowBox != null) finalDontShowBox.setEnabled(true);
                                            finalDialog1.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                                            timer.stop();
                                        } else {
                                            okButton.setText(LanguageProvider.get("gui.ok") + " (" + secondsLeft[0] + ")");
                                        }
                                    });
                                    timer.start();
                                }
                                CrashAssistantLocalConfig.set(configKey, true);
                            }

                            long showStartTime = System.currentTimeMillis();
                            dialog.setVisible(true);
                            CrashAssistantApp.LOGGER.info("Shown KnownCrashReason: {} (Seen warning for {}s)", crashReason.getClass().getSimpleName(), (System.currentTimeMillis() - showStartTime) / 1000.0);
                        }
                    } finally {
                        if (isHeadless) {
                            if (frame != null) frame.dispose();
                            frame = null;
                        }
                    }
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing known crash reasons warnings: ", e);
            }
        }
    }

    public static void showCrashAssistantDuplicatedWarning() {
        try {
            if (PlatformHelp.platform != PlatformHelp.FORGE &&
                    PlatformHelp.platform != PlatformHelp.NEOFORGE) return;
            List<Mod> mods = JarInJarHelper.checkDuplicatedCrashAssistantMod(false);
            if (mods.size() < 2) return;

            synchronized (KnownCrashReasonMessage.class) {
                KnownCrashReasonMessage.addCrashReasonMessage(
                        new KnownCrashReasonMessage(
                                null,
                                new DuplicatedCrashAssistantMod(mods)
                        )
                );
            }
            showKnownCrashReasonsWarnings();
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while showing crash assistant duplicated warning: ", e);
        }
    }

    private void showPiracyWarning() {
        if (!CrashAssistantConfig.getBoolean("piracy.enabled")) return;
        if (Objects.equals(CrashAssistantLocalConfig.get("piracy.dont_show_again"), true)) return;

        UUIDCheckStatus result = UUIDUtils.waitAndGetStatus();
        if (result != UUIDCheckStatus.PIRACY_OR_OFFLINE) return;

        PiracyWarning.showWarning(frame);
    }

    public static void showTooManyChangesWarning() {
        synchronized (KnownCrashReasonMessage.class) {
            try {
                try {
                    if (Objects.equals(CrashAssistantLocalConfig.get("too_many_changes.dont_show_again"), true)) {
                        return;
                    }
                } catch (Throwable ignored) {
                }

                int allowedChanges = CrashAssistantConfig.getInteger("too_many_changes_warning.count");
                if (allowedChanges <= 0) return;
                if (ModListDiff.isModpackCreator()) return;
                int totalChanges = ModListDiff.getDiff(true).getTotalChanges();
                if (totalChanges <= allowedChanges) return;
                String message;
                if (CrashAssistantConfig.get("too_many_changes_warning.formulation_type").equals("DROP_SUPPORT")) {
                    message = LanguageProvider.get("gui.too_many_changes_warning_drop_support");
                } else {
                    message = LanguageProvider.get("gui.too_many_changes_warning_notice");
                }
                message = message.replace("$MODIFICATIONS_COUNT$", "<strong style='color: red;'>" + totalChanges + "</strong>");

                ControlPanel.stopMovingToTop = true;
                String finalMessage = message;
                SwingUtilities.invokeAndWait(() -> {
                    JDialog dialog = new JDialog((Frame) null, LanguageProvider.get("gui.too_many_changes_title"), true);
                    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                    JEditorPane textPane = CrashAssistantGUI.getEditorPane(finalMessage, false);
                    JPanel textPanel = new JPanel(new BorderLayout());
                    textPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                            BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    ));
                    textPanel.add(textPane, BorderLayout.CENTER);

                    JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
                    dontShowAgainCheck.addActionListener(e ->
                            dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig.set("too_many_changes.dont_show_again", dontShowAgainCheck.isSelected())
                    );

                    JButton okButton = new JButton("OK");
                    okButton.addActionListener(e -> dialog.dispose());

                    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
                    bottomPanel.add(dontShowAgainCheck);
                    bottomPanel.add(okButton);

                    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
                    mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                    mainPanel.add(textPanel, BorderLayout.CENTER);
                    mainPanel.add(bottomPanel, BorderLayout.SOUTH);

                    dialog.setContentPane(mainPanel);
                    dialog.pack();
                    dialog.setLocationRelativeTo(null);
                    CrashAssistantApp.LOGGER.info("Showing too many changes warning");
                    dialog.setVisible(true);
                    CrashAssistantApp.LOGGER.info("Too many changes warning dialog closed");
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing too many changes warning: ", e);
            }
        }
    }

    public static void showEarlyIntegratedGPUWarning() {
        synchronized (KnownCrashReasonMessage.class) {
            try {
                if (Boot.getSerialisedGPUs() == null) return;
                if (CrashAssistantApp.renderer != null && !Objects.equals(CrashAssistantApp.renderer, "UNDEFINED"))
                    return;
                Log latest = null;
                for (Log log : LogsList.getLogs()) {
                    if (log.getType() == LogType.LOG) {
                        latest = log;
                        break;
                    }
                }
                if (latest == null) return;
                latest.getReader().readLogFileSafe();
                List<String> firstLines = latest.getReader().getFirstLinesList();
                String renderer = null;
                for (int i = 0; i < Math.min(firstLines.size(), 1000); i++) {
                    renderer = RendererParser.getRenderer(firstLines.get(i));
                    if (renderer != null) break;
                }
                if (renderer == null) return;
                if (Objects.equals(CrashAssistantApp.renderer, "UNDEFINED")) CrashAssistantApp.renderer = null;
                ProcessSignalIO.postAsOtherProcess("renderer", renderer, Boot.parentPID);
                CrashAssistantApp.LOGGER.info("Minecraft process have not reached out our renderer parsing hook, but successfully parsed renderer from logs: {}", renderer);
                CrashAssistantApp.checkRendererFile();
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing early IGPU warning: ", e);
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
                                            "<strong>" + String.join(", ", detectedMods.stream().map(Mod::getJarName).collect(Collectors.toList())) + " </strong>" +
                                            "are incompatible.\n" +
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
                                File modsDir = ModListUtils.MODS_FOLDER.toFile();
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
                            File modsDir = ModListUtils.MODS_FOLDER.toFile();
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
        // Get or create the state for the button, storing the original look only once.
        OriginalState state = highlightedComponents.computeIfAbsent(button, OriginalState::new);
        final int currentGeneration = ++state.generation;

        javax.swing.Timer timer = new javax.swing.Timer(400, null);
        final int[] count = {0};
        long startTime = Instant.now().toEpochMilli();

        final String highlightStyle = "disabledBackground: " + String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

        timer.addActionListener(e -> {
            // If a newer animation has started for this button, this timer is obsolete.
            if (state.generation != currentGeneration) {
                timer.stop();
                return;
            }

            if (Instant.now().toEpochMilli() - startTime > time) {
                timer.stop();
                // This is the last timer for this button, so restore the true original state.
                button.setBackground(state.originalBackground);
                button.putClientProperty("FlatLaf.style", state.originalStyle);
                button.repaint(); // Repaint needed for style change
                highlightedComponents.remove(button);
                return;
            }

            // Blinking logic: toggle between the new highlight color and the original state.
            boolean isHighlightPhase = count[0] % 2 == 0;

            // Always set both properties to cover all Look and Feels.
            button.setBackground(isHighlightPhase ? color : state.originalBackground);
            button.putClientProperty("FlatLaf.style", isHighlightPhase ? highlightStyle : state.originalStyle);
            button.repaint(); // Repaint is necessary for disabled buttons with FlatLaf

            count[0]++;
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
                } else if ("LANG.gui.dragging_and_dropping".equals(description)) {
                    UploadErrorDialog.show(frame, new Log(LogType.LOG, Paths.get("logs/latest.log")), "", true);
                    return;
                } else if ("CONFIG.text.support_name".equals(description)) {
                    componentToHighlight = ControlPanel.requestHelpButton;
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
                CrashAssistantGUI.highlightButton(componentToHighlight, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_attention_color"), new Color(100, 100, 255)), 3000);
            }
        };
    }

    public static JEditorPane getEditorPane(String text, boolean wrap) {
        return getEditorPane(text, wrap, null);
    }

    public static JEditorPane getEditorPane(String text, boolean wrap, Integer width) {
        JEditorPane pane = new JEditorPane() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (!wrap) {
                    d.width += 4; // Add extra margin to compensate for scaling rounding issues (e.g. at 125% DPI)
                }
                return d;
            }
        };
        pane.setEditable(false);
        pane.setContentType("text/html");

        String content = "<div " + (wrap ? "" : "style='white-space:nowrap;'") + ">" +
                text.replaceAll("\n", "<br>") + "</div>";

        pane.setText("<html><body>" + content + "</body></html>");

        if (width != null && pane.getPreferredSize().width - 180 > width) {
            pane.setText("<html><body style='width:" + width + "px;'>" + content + "</body></html>");
        }

        Font defaultFont = UIManager.getFont("Label.font");
        String bodyRule = "body { font-family: " + defaultFont.getFamily() + "; " +
                "font-size: " + defaultFont.getSize() + "pt; }";
        ((HTMLDocument) pane.getDocument()).getStyleSheet().addRule(bodyRule);

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
            if (fileListPanel.getFilePanelList().stream().noneMatch(x -> Objects.equals(x.getLog(), log))) {
                fileListPanel.addLog(log);
            }
        }
        CrashAssistantGUI.resize();
    }

    public static String getTitleCrashedText(boolean forMsg) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        String oops = forMsg ? "" : "_oops";
        return CrashAssistantApp.crashed_with_report ?
                langFunc.apply("gui.title_crashed_with_report" + oops) :
                langFunc.apply("gui.title_crashed_without_report" + oops);
    }

    public static JFrame getFrame() {
        return frame;
    }
}
