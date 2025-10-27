package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.CorruptedConfigFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.CorruptedJarFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.PackageFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.CreateDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.EpicFightDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.JdepsDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.MCreatorModDetectorGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.*;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.app.utils.TerminatedProcessesFinder;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.Lang;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.IncompatibleMod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;

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
    private static final Map<JComponent, OriginalState> highlightedComponents = new ConcurrentHashMap<>();

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

        HashMap<String, String> hrefOptions = new HashMap<String, String>() {{
            put("$CONFIG.text.support_name$", null);
            put("$LANG.gui.upload_all_comment$", null);
        }};

        String firstLinesOfComment = PlatformHelp.isLinkDefault() ?
                LanguageProvider.get("gui.comment_under_title_cant_resolve", hrefOptions) :
                LanguageProvider.get("gui.comment_under_title_pls_report", hrefOptions);

        String commentText = "<div style='margin-left: 5px;'>" + firstLinesOfComment + "\n" + LanguageProvider.get("gui.comment_under_title", hrefOptions) + "</div>";
        JEditorPane commentPane = getEditorPaneNoMargins(commentText, false);

        String screenshotNoticeText = LanguageProvider.get("gui.comment_under_title_screenshot_notice");
        String screenshotHtml = "<span style='color:red;'><b>" + screenshotNoticeText + "</b></span>";
        JEditorPane screenshotNoticePane = getEditorPaneNoMargins(screenshotHtml, false);
        if (showScreenshotNotice && CrashAssistantConfig.getBoolean("gui_customisation.screenshot_of_gui_notice_animated_border")) {
            screenshotNoticePane.setBorder(new AnimatedBorder(screenshotNoticePane, Color.RED, false));
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

        fileListPanel = new FileListPanel();
        frame.add(fileListPanel.getScrollPane(), BorderLayout.CENTER);

        controlPanel = new ControlPanel(fileListPanel);
        frame.add(controlPanel.getPanel(), BorderLayout.SOUTH);

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
        showTooManyChangesWarning();
        IntelChipBugWarning.showIfAffected(false);
        showEarlyIntegratedGPUWarning();
        new Thread(() -> {
            LogAnalyser.analyseLogs();
            showKnownCrashReasonsWarnings();
        }).start();
    }

    public static void setUpIcon(JFrame frame) {
        try {
            java.io.InputStream iconStream = JarInJarHelper.class.getResourceAsStream("/crash_assistant_ico.png");
            if (iconStream != null) {
                BufferedImage iconImage = ImageIO.read(iconStream);
                frame.setIconImage(iconImage);
                iconStream.close();
            } else {
                CrashAssistantApp.LOGGER.warn("Could not find crash_assistant_logo.png in jar root");
            }
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to load window icon", e);
        }
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

            // Mods loaded by Sinytra Connector analysis tool, not finished yet, will be in the next update.
//            if (!disabledByConfigTools.contains("SinytraConnectorModsAnalysisGUI")) {
//                JMenuItem sinytraConnectorItem = makeMenuItem.apply("gui.menu.analysis.sinytra_connector_mods", "gui.analysis.sinytra_connector_mods.header");
//                sinytraConnectorItem.addActionListener(e -> SinytraConnectorModsAnalysisGUI.showSinytraConnectorModsDialog(frame));
//                analysisMenu.add(sinytraConnectorItem);
//            }

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
        if (frame == null || fileListPanel == null) return;
        int old = fileListPanel.getScrollPane().getVerticalScrollBarPolicy();
        fileListPanel.getScrollPane().setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        frame.pack();
        frame.setSize(frame.getPreferredSize().width, Math.min(frame.getPreferredSize().height, 700));
        frame.setMinimumSize(new Dimension(frame.getSize().width, frame.getSize().height));
        fileListPanel.getScrollPane().setVerticalScrollBarPolicy(old);
        frame.repaint();
    }

    public static synchronized void showKnownCrashReasonsWarnings() {
        ControlPanel.stopMovingToTop = true;
        synchronized (KnownCrashReasonMessage.class) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (KnownCrashReasonMessage crashReasonMessage : KnownCrashReasonMessage.getAllMessages()) {
                        if (crashReasonMessage.isShownWarn()) continue;
                        KnownCrashReason crashReason = crashReasonMessage.getReason();
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
                                crashReasonMessage.isCodexMessage() ? crashReasonMessage.getMessage() : crashReasonMessage.getMessage().split("\n")[0] + "...");
                        crashReasonMessage.setShownWarn(true);

                        JEditorPane messagePane = CrashAssistantGUI.getEditorPane(crashReasonMessage.getMessage(), crashReasonMessage.isCodexMessage());

                        LinkedHashMap<String, Consumer<JDialog>> autoFixButtons = crashReason.getAutoFixButtons();

                        JDialog dialog;
                        if (!autoFixButtons.isEmpty()) {
                            JPanel autoFixPanel = new JPanel(new GridBagLayout());
                            GridBagConstraints gbc = new GridBagConstraints();
                            gbc.fill = GridBagConstraints.HORIZONTAL;
                            gbc.weightx = 1.0;
                            gbc.gridy = 0;

                            // Create a list to hold buttons, so we can add listeners later
                            List<JButton> buttons = new ArrayList<>();
                            List<Consumer<JDialog>> actions = new ArrayList<>();

                            for (Map.Entry<String, Consumer<JDialog>> entry : autoFixButtons.entrySet()) {
                                JButton autoFixButton = new JButton(entry.getKey());
                                autoFixButton.setFont(autoFixButton.getFont().deriveFont(Font.BOLD,
                                        CrashAssistantConfig.getInteger("gui_customisation.auto_fix_button_font_size")));
                                autoFixButton.setForeground(
                                        ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.auto_fix_button_foreground_color"),
                                                autoFixButton.getForeground()));
                                autoFixPanel.add(autoFixButton, gbc);
                                gbc.gridy++;
                                buttons.add(autoFixButton);
                                actions.add(entry.getValue());
                            }

                            JPanel mainPanel = new JPanel(new BorderLayout(10, 5));
                            mainPanel.add(messagePane, BorderLayout.CENTER);
                            mainPanel.add(autoFixPanel, BorderLayout.SOUTH);

                            JOptionPane optionPane = new JOptionPane(
                                    mainPanel,
                                    JOptionPane.WARNING_MESSAGE,
                                    JOptionPane.DEFAULT_OPTION
                            );

                            dialog = optionPane.createDialog(
                                    frame,
                                    crashReasonMessage.isCodexMessage() ? LanguageProvider.get("gui.codex_logs_analyzer") : LanguageProvider.get("gui.logs_analyzer")
                            );

                            // Add listeners now that the dialog is created
                            for (int i = 0; i < buttons.size(); i++) {
                                JButton button = buttons.get(i);
                                Consumer<JDialog> action = actions.get(i);
                                JDialog finalDialog = dialog;
                                button.addActionListener(e -> action.accept(finalDialog));
                            }
                        } else {
                            JOptionPane optionPane = new JOptionPane(
                                    messagePane,
                                    JOptionPane.WARNING_MESSAGE,
                                    JOptionPane.DEFAULT_OPTION
                            );
                            dialog = optionPane.createDialog(
                                    frame,
                                    crashReasonMessage.isCodexMessage() ? LanguageProvider.get("gui.codex_logs_analyzer") : LanguageProvider.get("gui.logs_analyzer")
                            );
                        }
                        long showStartTime = System.currentTimeMillis();
                        dialog.setVisible(true);
                        CrashAssistantApp.LOGGER.info("Shown KnownCrashReason: {} (Seen warning for {}s)", crashReason.getClass().getSimpleName(), (System.currentTimeMillis() - showStartTime) / 1000.0);
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

    public static void showTooManyChangesWarning() {
        synchronized (KnownCrashReasonMessage.class) {
            try {
                int allowedChanges = CrashAssistantConfig.getInteger("too_many_changes_warning.count");
                if (allowedChanges <= 0) return;
//                if (ModListDiff.isModpackCreator()) return;
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
                    JOptionPane optionPane = new JOptionPane(
                            CrashAssistantGUI.getEditorPane(finalMessage, false),
                            JOptionPane.WARNING_MESSAGE,
                            JOptionPane.DEFAULT_OPTION
                    );
                    JDialog dialog = optionPane.createDialog(
                            frame,
                            LanguageProvider.get("gui.too_many_changes_title")
                    );
                    dialog.setVisible(true);
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing too many changes warning: ", e);
            }
        }
    }

    public static void showEarlyIntegratedGPUWarning() {
        synchronized (KnownCrashReasonMessage.class) {
            try {
                if (Boot.serialisedGPUs == null) return;
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
        String oops = forMsg ? "" : "_oops";
        return CrashAssistantApp.crashed_with_report ?
                langFunc.apply("gui.title_crashed_with_report" + oops) :
                langFunc.apply("gui.title_crashed_without_report" + oops);
    }

    public static JFrame getFrame() {
        return frame;
    }
}
