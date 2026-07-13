package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.ControlPanel;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.utils.ClipboardUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.LinksHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.ModPlatformLookupService;
import dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.api.CurseForge;
import dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.api.Modrinth;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.UpdatedPair;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModFingerprinter;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Modern ModList Diff dialog with collapsible sections and platform-aware actions.
 */
public class ModListDiffDialog extends JFrame {
    private static ModListDiffDialog INSTANCE;
    private Window parentWindow;
    private static final ImageIcon CF_ICON = loadIcon("/assets/cf_logo.png");
    private static final ImageIcon MR_ICON = loadIcon("/assets/mr_logo.png");
    private final List<JButton> footerButtons = new ArrayList<JButton>();
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel progressLabel = new JLabel(" ");
    private final JPanel progressButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JButton cancelCurrentButton = new JButton(LanguageProvider.get("gui.modlist_diff.cancel_current"));
    private final JButton cancelAllButton = new JButton(LanguageProvider.get("gui.modlist_diff.cancel_all"));
    public static final Path tmpDownloadsFolder = ModListUtils.MODS_FOLDER.resolve(".crash_assistant_tmp");

    // Cancellation State Flags
    private volatile boolean cancelCurrentRequested = false;
    private volatile boolean cancelAllRequested = false;
    private volatile DiffEntry cancelTargetEntry = null;
    private volatile boolean preferModrinth = false;

    // State Tracking
    private volatile boolean lookupWarningShown = false;
    private volatile DiffEntry currentActionEntry = null;
    private volatile DiffEntry activeDownloadEntry = null;
    private volatile java.io.InputStream activeDownloadStream = null;
    private volatile java.net.HttpURLConnection activeDownloadConnection = null;
    private volatile Thread activeActionThread = null;

    private volatile boolean cfReady = false;
    private volatile boolean mrReady = false;

    private final ThreadPoolExecutor actionExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            r -> {
                Thread t = new Thread(r, "modlist-actions");
                t.setDaemon(true);
                return t;
            }
    );
    // Instant actions (disable/remove/reveal) shouldn't wait behind queued tasks.
    private final ExecutorService instantActionExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "modlist-instant-actions");
        t.setDaemon(true);
        return t;
    });
    private JButton disableToggleButton;

    public static void showDialog(Window parent) {
        boolean hasSavedModlist = !ModListUtils.getSavedModList().isEmpty();
        if (!CrashAssistantApp.gameLaunchedSuccessfully && !hasSavedModlist) {
            JOptionPane.showMessageDialog(
                    parent,
                    LanguageProvider.get("gui.modlist_diff.first_launch_warning"),
                    LanguageProvider.get("gui.modlist_diff_dialog_name"),
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        LinkedHashSet<Mod> savedMods = ModListUtils.getSavedModList();
        if (savedMods.size() >= 3) {
            boolean anyHasHash = false;
            for (Mod mod : savedMods) {
                if (mod.getCurseForgeHash() != null || mod.getModrinthHash() != null) {
                    anyHasHash = true;
                    break;
                }
            }
            if (!anyHasHash) {
                JOptionPane.showMessageDialog(
                        parent,
                        LanguageProvider.get("gui.modlist_diff.legacy_warning"),
                        LanguageProvider.get("gui.modlist_diff_dialog_name"),
                        JOptionPane.WARNING_MESSAGE
                );
            }
        }

        Window blockParent = parent;
        if (blockParent == null) blockParent = CrashAssistantGUI.getFrame();

        if (INSTANCE == null) {
            INSTANCE = new ModListDiffDialog(blockParent);
        } else {
            INSTANCE.parentWindow = blockParent;
        }
        INSTANCE.setLocationRelativeTo(parent);
        INSTANCE.setVisible(true);
        INSTANCE.toFront();
    }

    @Override
    public void setVisible(boolean b) {
        JFrame mainFrame = CrashAssistantGUI.getFrame();
        if (b) {
            if (parentWindow != null && parentWindow != mainFrame) {
                parentWindow.setVisible(false);
            }
            if (mainFrame != null) {
                mainFrame.setEnabled(false);
            }
            super.setVisible(true);
        } else {
            super.setVisible(false);
            if (mainFrame != null) {
                mainFrame.setEnabled(true);
            }
            if (parentWindow != null && parentWindow != mainFrame) {
                SwingUtilities.invokeLater(() -> {
                    parentWindow.setVisible(true);
                    parentWindow.toFront();
                });
            } else if (mainFrame != null) {
                SwingUtilities.invokeLater(() -> {
                    mainFrame.toFront();
                });
            }
        }
    }

    enum SectionType {ADDED, UPDATED, REMOVED}

    enum SectionAction {REMOVE, DISABLE, REVERT, RESTORE, ENABLE, SHOW_FOLDER}

    enum ActionState {IDLE, RUNNING, DONE}

    private final List<DiffEntry> addedEntries = new ArrayList<DiffEntry>();
    private final List<DiffEntry> updatedEntries = new ArrayList<DiffEntry>();
    private final List<DiffEntry> removedEntries = new ArrayList<DiffEntry>();

    private final Map<SectionType, SectionPanel> sectionPanels = new EnumMap<SectionType, SectionPanel>(SectionType.class);
    private ModPlatformLookupService.LookupResult lookupResult;

    private final JLabel statusLabel = new JLabel(" ");
    private final JButton applyButton = new JButton();

    private ModListDiffDialog(Window parent) {
        super(LanguageProvider.get("gui.modlist_diff_dialog_name"));
        this.parentWindow = parent;
        CrashAssistantGUI.setUpIcon(this);
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        ModListDiff diff = ModListDiff.getDiff(true);
        populateEntries(diff);

        lookupResult = new ModPlatformLookupService.LookupResult(Collections.<Long, CurseForge.FingerprintMatch>emptyMap(),
                Collections.<String, Modrinth.VersionFileInfo>emptyMap());

        buildUi();
        startLookupAsync();
        pack();

        Dimension packedSize = getSize();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int targetHeight = (int) (screen.height * 0.8);

        int width = packedSize.width;
        // If the content is taller than our target height, a vertical scrollbar will appear.
        // We need to add its width to prevent horizontal content clipping.
        if (packedSize.height > targetHeight) {
            width += new JScrollBar(JScrollBar.VERTICAL).getPreferredSize().width;
        }

        setMinimumSize(new Dimension(width, 300));
        setSize(new Dimension(width, targetHeight));
    }

    private void populateEntries(ModListDiff diff) {
        for (Mod added : diff.getAddedMods()) {
            addedEntries.add(new DiffEntry(SectionType.ADDED, added, null));
        }

        for (UpdatedPair pair : diff.getUpdatedMods()) {
            updatedEntries.add(new DiffEntry(pair));
        }

        for (Mod removed : diff.getRemovedMods()) {
            removedEntries.add(new DiffEntry(SectionType.REMOVED, null, removed));
        }
    }

    private Set<Long> collectFingerprints(boolean includeSaved) {
        LinkedHashSet<Long> set = new LinkedHashSet<Long>();
        for (DiffEntry entry : allEntries()) {
            set.addAll(entry.getCurseHashes(includeSaved));
        }
        return set;
    }

    private Set<String> collectHashFingerprints(boolean includeSaved) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        for (DiffEntry entry : allEntries()) {
            set.addAll(entry.getModrinthHashes(includeSaved));
        }
        return set;
    }

    private List<DiffEntry> allEntries() {
        List<DiffEntry> list = new ArrayList<DiffEntry>();
        list.addAll(addedEntries);
        list.addAll(updatedEntries);
        list.addAll(removedEntries);
        return list;
    }

    private void applyLookupResults() {
        for (DiffEntry entry : allEntries()) {
            applyMatches(entry, lookupResult);
        }
    }

    private void startLookupAsync() {
        new Thread(() -> {
            ModPlatformLookupService lookupService = new ModPlatformLookupService();
            Set<Long> cfFingerprints = collectFingerprints(true);
            Set<String> mrFingerprints = collectHashFingerprints(true);
            JLabel fetchingLabel = new JLabel(LanguageProvider.get("gui.modlist_diff.fetching_warning"));
            fetchingLabel.setForeground(Color.GRAY);
            SwingUtilities.invokeLater(() -> statusLabel.setText(fetchingLabel.getText()));

            Runnable cfTask = () -> {
                Map<Long, CurseForge.FingerprintMatch> cfResult = lookupCurseForge(lookupService, cfFingerprints);
                SwingUtilities.invokeLater(() -> applyLookupUpdate(cfResult, Collections.<String, Modrinth.VersionFileInfo>emptyMap(), true, null));
            };
            Runnable mrTask = () -> {
                Map<String, Modrinth.VersionFileInfo> mrResult = lookupModrinth(lookupService, mrFingerprints);
                SwingUtilities.invokeLater(() -> applyLookupUpdate(Collections.<Long, CurseForge.FingerprintMatch>emptyMap(), mrResult, null, true));
            };

            Thread cfThread = new Thread(cfTask, "modlist-lookup-cf");
            cfThread.setDaemon(true);
            Thread mrThread = new Thread(mrTask, "modlist-lookup-mr");
            mrThread.setDaemon(true);
            cfThread.start();
            mrThread.start();
        }, "modlist-lookup").start();
    }

    private Map<Long, CurseForge.FingerprintMatch> lookupCurseForge(ModPlatformLookupService lookupService,
                                                                    Set<Long> cfFingerprints) {
        Map<Long, CurseForge.FingerprintMatch> cfMap = null;
        try {
            ModPlatformLookupService.LookupResult cfRes = lookupService.lookup(cfFingerprints, Collections.<String>emptySet());
            cfMap = cfRes != null ? cfRes.getCurseForgeMatches() : null;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("CurseForge lookup failed", e);
            if (isConnectionIssue(e)) {
                try {
                    ModPlatformLookupService.LookupResult cfRes = lookupService.lookup(cfFingerprints, Collections.<String>emptySet());
                    cfMap = cfRes != null ? cfRes.getCurseForgeMatches() : null;
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.warn("CurseForge retry failed", ex);
                }
            }
        }
        return cfMap == null ? Collections.<Long, CurseForge.FingerprintMatch>emptyMap() : cfMap;
    }

    private Map<String, Modrinth.VersionFileInfo> lookupModrinth(ModPlatformLookupService lookupService,
                                                                 Set<String> mrFingerprints) {
        Map<String, Modrinth.VersionFileInfo> mrMap = null;
        try {
            ModPlatformLookupService.LookupResult mrRes = lookupService.lookup(Collections.<Long>emptySet(), mrFingerprints);
            mrMap = mrRes != null ? mrRes.getModrinthMatches() : null;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("Modrinth lookup failed", e);
            if (isConnectionIssue(e)) {
                try {
                    ModPlatformLookupService.LookupResult mrRes = lookupService.lookup(Collections.<Long>emptySet(), mrFingerprints);
                    mrMap = mrRes != null ? mrRes.getModrinthMatches() : null;
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.warn("Modrinth retry failed", ex);
                }
            }
        }
        return mrMap == null ? Collections.<String, Modrinth.VersionFileInfo>emptyMap() : mrMap;
    }

    private void applyLookupUpdate(Map<Long, CurseForge.FingerprintMatch> cfMap,
                                   Map<String, Modrinth.VersionFileInfo> mrMap,
                                   Boolean cfDone,
                                   Boolean mrDone) {
        Map<Long, CurseForge.FingerprintMatch> newCf = new HashMap<Long, CurseForge.FingerprintMatch>(lookupResult.getCurseForgeMatches());
        Map<String, Modrinth.VersionFileInfo> newMr = new HashMap<String, Modrinth.VersionFileInfo>(lookupResult.getModrinthMatches());
        if (cfMap != null) newCf.putAll(cfMap);
        if (mrMap != null) newMr.putAll(mrMap);
        lookupResult = new ModPlatformLookupService.LookupResult(newCf, newMr);
        applyLookupResults(lookupResult);
        if (cfDone != null) cfReady = cfDone;
        if (mrDone != null) mrReady = mrDone;
        refreshTables();
        updateStatusLabel();
    }

    private void applyLookupResults(ModPlatformLookupService.LookupResult res) {
        for (DiffEntry entry : allEntries()) {
            applyMatches(entry, res);
        }
    }

    private void applyMatches(DiffEntry entry, ModPlatformLookupService.LookupResult res) {
        if (res == null) return;
        for (DiffEntry.ModInstance mi : entry.currentMods) {
            if (mi.curseHash != null) {
                mi.curseMatch = res.getCurseForgeMatches().get(mi.curseHash);
            }
            if (mi.modrinthHash != null) {
                mi.modrinthMatch = res.getModrinthMatches().get(mi.modrinthHash);
            }
        }
        for (DiffEntry.ModInstance mi : entry.savedMods) {
            if (mi.curseHash != null) {
                mi.curseMatch = res.getCurseForgeMatches().get(mi.curseHash);
            }
            if (mi.modrinthHash != null) {
                mi.modrinthMatch = res.getModrinthMatches().get(mi.modrinthHash);
            }
        }
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(12, 12, 10, 12));
        JLabel title = new JLabel(ModListDiff.getFirstString(false, false, null));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        JPanel sectionsContainer = new JPanel();
        sectionsContainer.setLayout(new BoxLayout(sectionsContainer, BoxLayout.Y_AXIS));
        sectionsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionsContainer.setAlignmentY(Component.TOP_ALIGNMENT);
        SectionPanel addedPanel = new SectionPanel(this, SectionType.ADDED, addedEntries);
        sectionsContainer.add(wrapTop(addedPanel.getComponent()));
        sectionsContainer.add(Box.createVerticalStrut(4));
        SectionPanel updatedPanel = new SectionPanel(this, SectionType.UPDATED, updatedEntries);
        sectionsContainer.add(wrapTop(updatedPanel.getComponent()));
        sectionsContainer.add(Box.createVerticalStrut(4));
        SectionPanel removedPanel = new SectionPanel(this, SectionType.REMOVED, removedEntries);
        sectionsContainer.add(wrapTop(removedPanel.getComponent()));
        sectionsContainer.add(Box.createVerticalGlue());

        sectionPanels.put(SectionType.ADDED, addedPanel);
        sectionPanels.put(SectionType.UPDATED, updatedPanel);
        sectionPanels.put(SectionType.REMOVED, removedPanel);

        JScrollPane scrollPane = new JScrollPane(sectionsContainer);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setAlignmentY(0f);
        scrollPane.getViewport().setAlignmentX(0f);
        add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(10, 12, 10, 12));
        statusLabel.setForeground(new Color(70, 70, 70));
        footer.add(statusLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copyDiff = new JButton(LanguageProvider.get("gui.modlist_diff.copy_diff"));
        copyDiff.addActionListener(e -> copyDiffWithFeedback(copyDiff));
        buttons.add(copyDiff);
        footerButtons.add(copyDiff);
        disableToggleButton = new JButton(LanguageProvider.get("gui.files_remover.disable_selected"));
        disableToggleButton.addActionListener(e -> {
            if (allSelectedDisabled()) bulkApply(SectionAction.ENABLE);
            else bulkApply(SectionAction.DISABLE);
        });
        buttons.add(disableToggleButton);
        footerButtons.add(disableToggleButton);
        JButton removeSelected = new JButton(LanguageProvider.get("gui.files_remover.remove_selected"));
        removeSelected.addActionListener(e -> bulkApply(SectionAction.REMOVE));
        buttons.add(removeSelected);
        footerButtons.add(removeSelected);
        updateApplyButtonLabel();
        buttons.add(applyButton);
        footerButtons.add(applyButton);
        footer.add(buttons, BorderLayout.EAST);

        JPanel progressRow = new JPanel(new BorderLayout(6, 0));
        progressBar.setPreferredSize(new Dimension(180, 16));
        progressBar.setVisible(false);
        progressRow.add(progressLabel, BorderLayout.WEST);
        progressRow.add(progressBar, BorderLayout.CENTER);

        cancelCurrentButton.addActionListener(e -> requestCancelCurrentOperation());
        cancelAllButton.addActionListener(e -> requestCancelAllOperations());
        progressButtonsPanel.add(cancelCurrentButton);
        progressButtonsPanel.add(cancelAllButton);
        progressButtonsPanel.setVisible(false);

        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.setBorder(new EmptyBorder(6, 12, 10, 12));
        progressPanel.add(progressRow);
        progressPanel.add(Box.createVerticalStrut(6));
        progressPanel.add(progressButtonsPanel);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.add(footer, BorderLayout.NORTH);
        bottomBar.add(progressPanel, BorderLayout.SOUTH);

        add(bottomBar, BorderLayout.SOUTH);

        applyButton.addActionListener(e -> performBulkActions());
        updateStatusLabel();
    }

    private void bulkApply(SectionAction action) {
        List<DiffEntry> targets = new ArrayList<DiffEntry>();
        for (DiffEntry e : allEntries()) {
            if (!e.selected || e.resolved) continue;
            if (action == SectionAction.REVERT && e.type != SectionType.UPDATED) continue;
            if ((action == SectionAction.DISABLE || action == SectionAction.ENABLE) && e.type == SectionType.REMOVED)
                continue;
            if (action == SectionAction.REMOVE && e.type == SectionType.REMOVED) continue;
            targets.add(e);
        }
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    LanguageProvider.get("gui.files_remover.select_first_warning_body"),
                    LanguageProvider.get("gui.files_remover.select_first_warning_title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (action == SectionAction.REMOVE) {
            int res = JOptionPane.showConfirmDialog(
                    this,
                    "You are going to remove " + targets.size() + " mod(s). Are you sure?",
                    LanguageProvider.get("gui.modlist_diff_dialog_name"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (res != JOptionPane.YES_OPTION) {
                return;
            }
        }
        setAllActionButtonsEnabled(false);
        ExecutorService executor = executorFor(action);
        executor.submit(() -> {
            Thread actionThread = null;
            if (!isInstantAction(action)) {
                actionThread = Thread.currentThread();
                activeActionThread = actionThread;
            }
            try {
                // IMPORTANT: Clear stale interrupts from previous cancellations before starting new batch
                clearInterruptFlag();
                for (DiffEntry entry : targets) {
                    clearInterruptFlag(); // Ensure this iteration starts clean
                    if (isCancelAllRequested()) break;

                    currentActionEntry = entry;
                    startAction(entry, action);
                    SwingUtilities.invokeLater(() -> refreshTables());

                    boolean success = performAction(entry, action);

                    finishAction(entry, action, success);
                    clearSingleCancelFor(entry); // Just in case it wasn't cleared inside, though it should be
                    currentActionEntry = null;

                    if (isCancelAllRequested()) break;
                }
                SwingUtilities.invokeLater(() -> {
                    refreshTables();
                    setAllActionButtonsEnabled(true);
                    updateStatusLabel();
                    resetProgress();
                    clearCancelAllFlag();
                });
            } finally {
                if (actionThread != null && activeActionThread == actionThread) {
                    activeActionThread = null;
                }
                clearInterruptFlag();
            }
        });
    }

    void updateStatusLabel() {
        int added = countSelected(addedEntries);
        int updated = countSelected(updatedEntries);
        int removed = countSelected(removedEntries);
        String msg = LanguageProvider.get("gui.modlist_diff.footer.counts")
                .replace("$ADDED$", Integer.toString(added))
                .replace("$UPDATED$", Integer.toString(updated))
                .replace("$REMOVED$", Integer.toString(removed));
        statusLabel.setText(msg);
        statusLabel.setForeground(new Color(70, 70, 70));
        if (!cfReady && !mrReady) {
            statusLabel.setText(LanguageProvider.get("gui.modlist_diff.fetching_warning"));
        }
        updateDisableToggleLabel();
        updateApplyButtonLabel();
    }

    private void copyDiffWithFeedback(JButton button) {
        ClipboardUtils.copy(ModListDiff.getDiff(true).generateDiffMsg(true).toText());
        String originalText = LanguageProvider.get("gui.modlist_diff.copy_diff");
        button.setText(LanguageProvider.get("gui.copied"));
        CrashAssistantGUI.highlightButton(button, ControlPanel.deserializeColor(CrashAssistantConfig.get("gui_customisation.blinking_button_success_color"), new Color(100, 255, 100)), 2600);
        button.setEnabled(false);
        new Timer("copy-diff-feedback", true).schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    button.setText(originalText);
                    button.setEnabled(true);
                });
            }
        }, 2800);
    }

    private int countSelected(List<DiffEntry> entries) {
        int c = 0;
        for (DiffEntry e : entries) {
            if (e.selected && !e.resolved) c++;
        }
        return c;
    }

    private void performBulkActions() {
        List<DiffEntry> toRemove = selectedOf(addedEntries);
        List<DiffEntry> toRevert = selectedOf(updatedEntries);
        List<DiffEntry> toRestore = selectedOf(removedEntries);
        if (toRemove.isEmpty() && toRevert.isEmpty() && toRestore.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    LanguageProvider.get("gui.files_remover.select_first_warning_body"),
                    LanguageProvider.get("gui.files_remover.select_first_warning_title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String confirmMsg = LanguageProvider.get("gui.modlist_diff.confirm.body")
                .replace("$REMOVE$", Integer.toString(toRemove.size()))
                .replace("$REVERT$", Integer.toString(toRevert.size()))
                .replace("$DOWNLOAD$", Integer.toString(toRestore.size()));
        int res = JOptionPane.showConfirmDialog(this, confirmMsg,
                LanguageProvider.get("gui.modlist_diff.confirm.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;

        cancelCurrentRequested = false;
        cancelAllRequested = false;
        cancelTargetEntry = null;
        setAllActionButtonsEnabled(false);
        ControlPanel.stopMovingToTop = true;

        // Mark queued items as running so the UI shows them disabled immediately.
        for (DiffEntry entry : toRevert) {
            startAction(entry, SectionAction.REVERT);
        }
        for (DiffEntry entry : toRestore) {
            startAction(entry, SectionAction.RESTORE);
        }
        SwingUtilities.invokeLater(() -> {
            refreshTables();
            updateStatusLabel();
        });

        actionExecutor.submit(() -> {
            Thread actionThread = Thread.currentThread();
            activeActionThread = actionThread;
            try {
                clearInterruptFlag();
                for (DiffEntry entry : toRemove) {
                    clearInterruptFlag();
                    if (isCancelAllRequested()) break;
                    currentActionEntry = entry;
                    performAction(entry, SectionAction.REMOVE);
                    currentActionEntry = null;
                }
                for (DiffEntry entry : toRevert) {
                    clearInterruptFlag();
                    if (isCancelAllRequested()) break;
                    currentActionEntry = entry;
                    boolean success = performAction(entry, SectionAction.REVERT);
                    finishAction(entry, SectionAction.REVERT, success);
                    // CRITICAL: Ensure single cancel was consumed and cleared
                    clearSingleCancelFor(entry);
                    currentActionEntry = null;
                    if (isCancelAllRequested()) break;
                }
                for (DiffEntry entry : toRestore) {
                    clearInterruptFlag();
                    if (isCancelAllRequested()) break;
                    currentActionEntry = entry;
                    startAction(entry, SectionAction.RESTORE);
                    SwingUtilities.invokeLater(() -> {
                        refreshTables();
                        updateStatusLabel();
                    });
                    boolean success = performAction(entry, SectionAction.RESTORE);
                    finishAction(entry, SectionAction.RESTORE, success);
                    clearSingleCancelFor(entry);
                    currentActionEntry = null;
                    if (isCancelAllRequested()) break;
                }
                SwingUtilities.invokeLater(() -> {
                    refreshTables();
                    setAllActionButtonsEnabled(true);
                    updateStatusLabel();
                    resetProgress();
                    clearCancelAllFlag();
                });
            } finally {
                if (activeActionThread == actionThread) {
                    activeActionThread = null;
                }
                clearInterruptFlag();
            }
        });
    }

    private boolean isInstantAction(SectionAction action) {
        return action == SectionAction.DISABLE
                || action == SectionAction.ENABLE
                || action == SectionAction.REMOVE
                || action == SectionAction.SHOW_FOLDER;
    }

    private ExecutorService executorFor(SectionAction action) {
        return isInstantAction(action) ? instantActionExecutor : actionExecutor;
    }

    void runActionAsync(final DiffEntry entry, final SectionAction action) {
        ControlPanel.stopMovingToTop = true;

        // Reset single cancel state for THIS specific new action
        clearInterruptFlag();
        cancelCurrentRequested = false;
        cancelTargetEntry = null;

        // If we are running a new action, we shouldn't have leftover states
        if (!isInstantAction(action)) {
            // Note: We do NOT reset cancelAllRequested here, because the user might have clicked "Cancel All"
            // just as a new action was being spawned by code (rare, but safer).
            cancelCurrentRequested = false;
            cancelTargetEntry = null;
            currentActionEntry = entry;
        }

        startAction(entry, action);
        refreshTables();
        updateStatusLabel();

        executorFor(action).submit(() -> {
            Thread actionThread = null;
            if (!isInstantAction(action)) {
                actionThread = Thread.currentThread();
                activeActionThread = actionThread;
            }
            try {
                clearInterruptFlag();
                boolean success = performAction(entry, action);
                SwingUtilities.invokeLater(() -> {
                    finishAction(entry, action, success);
                    refreshTables();
                    updateStatusLabel();

                    if (isCancelAllRequested()) {
                        resetProgress();
                    }

                    // Cleanup this entry's cancel state
                    clearSingleCancelFor(entry);

                    if (!isInstantAction(action)) {
                        currentActionEntry = null;
                    }
                });
            } finally {
                if (actionThread != null && activeActionThread == actionThread) {
                    activeActionThread = null;
                }
                clearInterruptFlag();
            }
        });
    }

    private List<DiffEntry> selectedOf(List<DiffEntry> entries) {
        List<DiffEntry> list = new ArrayList<DiffEntry>();
        for (DiffEntry e : entries) {
            if (e.selected && !e.resolved) list.add(e);
        }
        return list;
    }

    private void refreshTables() {
        for (SectionPanel panel : sectionPanels.values()) {
            panel.refresh();
        }
    }

    String versionLabel(Mod mod) {
        if (mod == null) return "-";
        String version = mod.getVersion();
        String name = mod.getJarName();
        if (version == null || version.isEmpty()) return name;
        if (mod.isModMessedUpWithVersion()) {
            return name + " (" + version + ")";
        }
        return version;
    }

    private void startAction(DiffEntry entry, SectionAction action) {
        if (action == SectionAction.REVERT) entry.revertState = ActionState.RUNNING;
        if (action == SectionAction.RESTORE) entry.restoreState = ActionState.RUNNING;
    }

    private void finishAction(DiffEntry entry, SectionAction action, boolean success) {
        if (action == SectionAction.REVERT) entry.revertState = success ? ActionState.DONE : ActionState.IDLE;
        if (action == SectionAction.RESTORE) entry.restoreState = success ? ActionState.DONE : ActionState.IDLE;
        if (success) {
            if (action == SectionAction.REVERT || action == SectionAction.REMOVE || action == SectionAction.RESTORE) {
                entry.resolvedBy = action;
            }
        }
    }

    private void requestCancelCurrentOperation() {
        // Force cancel immediately regardless of what target the logic thinks is active.
        // We are single-threaded (mostly), so if the user clicks cancel, they mean "Stop everything now".
        cancelCurrentRequested = true;

        // Update UI immediately so the user gets feedback even if network clean up hangs
        SwingUtilities.invokeLater(() -> {
            progressLabel.setText(LanguageProvider.get("gui.modlist_diff.cancelling_current"));
            refreshTables();
        });

        // Offload the blocking I/O (closing socket) to a background thread.
        // If the network is saturated, connection.disconnect() or stream.close() can block for seconds.
        // Doing this on the EDT freezes the GUI.
        instantActionExecutor.submit(() -> {
            // Pass null to force abort any stream
            abortRunningDownload(null);
            interruptActiveActionThread(null);
        });
    }

    private void requestCancelAllOperations() {
        cancelAllRequested = true;

        // Also trigger current cancel to stop active task immediately
        cancelCurrentRequested = true;
        cancelTargetEntry = null; // targets whatever is running

        // Update UI immediately
        SwingUtilities.invokeLater(() -> {
            cancelCurrentButton.setEnabled(false);
            cancelAllButton.setEnabled(false);
            progressLabel.setText(LanguageProvider.get("gui.modlist_diff.cancelling_all"));
            refreshTables();
        });

        // Offload blocking cleanups
        instantActionExecutor.submit(() -> {
            abortRunningDownload(null);
            interruptActiveActionThread(null);

            actionExecutor.getQueue().clear();

            SwingUtilities.invokeLater(() -> {
                resetQueuedRunningStatesAfterCancelAll();
                markEntryIdle(currentActionEntry);
                clearCancelFlagsIfIdle();
            });
        });
    }

    private boolean isCancelRequestedFor(DiffEntry entry) {
        if (cancelAllRequested) return true;
        // If cancel is requested, we assume it applies to the currently running entry
        return cancelCurrentRequested;
    }

    private boolean isCancelInProgressFor(DiffEntry entry) {
        // Used for UI disabling logic
        if (cancelAllRequested) return true;
        // Simplified check: if cancel is requested, we consider it in progress for the active entry
        return cancelCurrentRequested && (currentActionEntry == entry || activeDownloadEntry == entry);
    }

    private boolean isCancelAllRequested() {
        return cancelAllRequested;
    }

    private void rememberActiveDownload(DiffEntry entry, java.io.InputStream stream, java.net.HttpURLConnection connection) {
        activeDownloadEntry = entry;
        activeDownloadStream = stream;
        activeDownloadConnection = connection;
    }

    private void clearActiveDownload(java.io.InputStream stream) {
        if (activeDownloadStream == stream) {
            activeDownloadStream = null;
            activeDownloadEntry = null;
            activeDownloadConnection = null;
        }
    }

    private void abortRunningDownload(DiffEntry target) {
        java.io.InputStream stream = activeDownloadStream;
        java.net.HttpURLConnection connection = activeDownloadConnection;

        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }

        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }

        if (stream == null) {
            if (connection != null && activeDownloadConnection == connection) {
                activeDownloadConnection = null;
                activeDownloadEntry = null;
            }
        } else {
            if (activeDownloadStream == stream) {
                activeDownloadStream = null;
                activeDownloadEntry = null;
                activeDownloadConnection = null;
            }
        }
    }

    private void interruptActiveActionThread(DiffEntry target) {
        Thread t = activeActionThread;
        if (t == null) return;
        try {
            t.interrupt();
        } catch (Exception ignored) {
        }
    }

    private void updateProgress(String text, boolean indeterminate) {
        updateProgress(text, indeterminate, -1);
    }

    private void updateProgress(String text, boolean indeterminate, int percent) {
        SwingUtilities.invokeLater(() -> {
            progressLabel.setText(text);
            progressBar.setVisible(true);
            progressBar.setIndeterminate(indeterminate);
            progressButtonsPanel.setVisible(true);
            cancelCurrentButton.setEnabled(true);
            cancelAllButton.setEnabled(true);
            if (!indeterminate && percent >= 0) {
                progressBar.setValue(percent);
            }
        });
    }

    private void resetProgress() {
        SwingUtilities.invokeLater(() -> {
            progressLabel.setText(" ");
            progressBar.setVisible(false);
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressButtonsPanel.setVisible(false);
            cancelCurrentButton.setEnabled(true);
            cancelAllButton.setEnabled(true);

            // Only clear flags if we aren't in the middle of something else,
            // or if this was a forced reset.
            cancelCurrentRequested = false;
            cancelAllRequested = false;
            cancelTargetEntry = null;
            activeDownloadEntry = null;
            activeDownloadStream = null;
            activeDownloadConnection = null;
            statusLabel.setForeground(new Color(70, 70, 70));
        });
    }

    private void clearInterruptFlag() {
        if (Thread.interrupted()) {
            // intentionally clearing stale interrupt state to prevent next task from dying instantly
        }
    }

    private void clearCancelAllFlag() {
        cancelAllRequested = false;
    }

    // Helper to CONSUME the single cancel flag so it doesn't bleed into the next task
    private void consumeSingleCancel() {
        if (cancelCurrentRequested) {
            cancelCurrentRequested = false;
            cancelTargetEntry = null;
            resetProgress();
        }
    }

    private void clearSingleCancelFor(DiffEntry entry) {
        // Just delegate to the consume method since we want to clear it anyway if it was set
        consumeSingleCancel();
    }

    private void clearCancelFlagsIfIdle() {
        if (actionExecutor.getActiveCount() == 0 && actionExecutor.getQueue().isEmpty()) {
            cancelAllRequested = false;
            cancelCurrentRequested = false;
            resetProgress();
        }
    }

    private void resetQueuedRunningStatesAfterCancelAll() {
        for (DiffEntry entry : allEntries()) {
            if (entry == currentActionEntry) continue;
            if (entry.revertState == ActionState.RUNNING) {
                entry.revertState = ActionState.IDLE;
            }
            if (entry.restoreState == ActionState.RUNNING) {
                entry.restoreState = ActionState.IDLE;
            }
        }
    }

    private void markEntryIdle(DiffEntry entry) {
        if (entry == null) return;
        if (entry.revertState == ActionState.RUNNING) {
            entry.revertState = ActionState.IDLE;
        }
        if (entry.restoreState == ActionState.RUNNING) {
            entry.restoreState = ActionState.IDLE;
        }
    }

    private void addWarning(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
            statusLabel.setForeground(new Color(180, 60, 60));
        });
    }

    private void warnLookupNotReady() {
        if (lookupWarningShown) return;
        lookupWarningShown = true;
        addWarning(LanguageProvider.get("gui.modlist_diff.wait_for_fetch"));
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this,
                LanguageProvider.get("gui.modlist_diff.wait_for_fetch"),
                LanguageProvider.get("gui.modlist_diff_dialog_name"),
                JOptionPane.WARNING_MESSAGE
        ));
    }

    private boolean isLookupReadyForEntry(DiffEntry entry) {
        boolean needsCf = !entry.getCurseHashes(true).isEmpty();
        boolean needsMr = !entry.getModrinthHashes(true).isEmpty();
        if (!needsCf && !needsMr) return true;
        boolean cfOk = !needsCf || cfReady;
        boolean mrOk = !needsMr || mrReady;
        return cfOk || mrOk;
    }

    private boolean isConnectionIssue(Throwable t) {
        if (t == null) return false;
        if (t instanceof java.io.IOException) return true;
        return isConnectionIssue(t.getCause());
    }

    private void setAllActionButtonsEnabled(boolean enabled) {
        applyButton.setEnabled(enabled);
        // Also disable/enable bulk buttons in footer
        if (footerButtons != null) {
            for (JButton b : footerButtons) {
                b.setEnabled(enabled);
            }
        }
    }

    private boolean allSelectedDisabled() {
        boolean anySelected = false;
        for (DiffEntry e : allEntries()) {
            if (e.selected && !e.resolved) {
                if (e.type == SectionType.REMOVED) continue;
                anySelected = true;
                if (!isDisabledEntry(e)) return false;
            }
        }
        return anySelected;
    }

    boolean isDisabledEntry(DiffEntry entry) {
        return entry != null && entry.areAllCurrentDisabled();
    }

    private void updateDisableToggleLabel() {
        if (disableToggleButton == null) return;
        if (allSelectedDisabled()) {
            disableToggleButton.setText(LanguageProvider.get("gui.files_remover.enable_selected"));
        } else {
            disableToggleButton.setText(LanguageProvider.get("gui.files_remover.disable_selected"));
        }
    }

    private void updateApplyButtonLabel() {
        if (applyButton == null) return;
        String key = ModListDiff.isModpackCreator()
                ? "gui.modlist_diff.footer.apply"
                : "gui.modlist_diff.footer.apply.modpack";
        applyButton.setText(LanguageProvider.get(key));
    }

    private boolean performAction(DiffEntry entry, SectionAction action) {
        if (entry.modloaderEntry) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    LanguageProvider.get("gui.modlist_diff.modloader_warning"),
                    LanguageProvider.get("gui.modlist_diff_dialog_name"),
                    JOptionPane.WARNING_MESSAGE
            ));
            addWarning(LanguageProvider.get("gui.modlist_diff.modloader_warning"));
            return false;
        }
        if ((action == SectionAction.REVERT || action == SectionAction.RESTORE) && !isLookupReadyForEntry(entry)) {
            warnLookupNotReady();
            return false;
        }
        switch (action) {
            case REMOVE:
                return removeEntry(entry);
            case DISABLE:
                return toggleDisable(entry, true);
            case ENABLE:
                return toggleDisable(entry, false);
            case REVERT:
                return revertEntry(entry);
            case RESTORE:
                return restoreEntry(entry);
            case SHOW_FOLDER:
                openFolder(entry);
                return false;
            default:
                return false;
        }
    }

    private boolean removeEntry(DiffEntry entry) {
        List<Path> targets = entry.currentPaths();
        if (targets.isEmpty()) {
            targets = entry.savedPaths();
        }
        if (targets.isEmpty()) return false;
        boolean ok = true;
        for (Path p : targets) {
            if (p == null) continue;
            try {
                Files.deleteIfExists(p);
            } catch (Exception e) {
                ok = false;
                CrashAssistantApp.LOGGER.error("Failed to remove {}", p, e);
            }
        }
        if (ok) {
            entry.resolved = true;
            entry.removedByAction = true;
            entry.resolvedBy = SectionAction.REMOVE;
        }
        return ok;
    }

    private boolean toggleDisable(DiffEntry entry, boolean disable) {
        List<DiffEntry.ModInstance> mods = entry.currentMods;
        if (mods.isEmpty()) {
            entry.resolved = true;
            return true;
        }
        try {
            for (DiffEntry.ModInstance mi : mods) {
                Path path = resolveExistingPath(mi.path, mi);
                if (path == null) continue;
                boolean currentlyDisabled = path.getFileName().toString().endsWith(".disabled");
                if (disable && currentlyDisabled) continue;
                if (!disable && !currentlyDisabled) continue;
                Path target;
                if (currentlyDisabled) {
                    String newName = path.getFileName().toString().replaceFirst("\\.disabled$", "");
                    target = path.resolveSibling(newName);
                } else {
                    target = path.resolveSibling(path.getFileName().toString() + ".disabled");
                }
                Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
                mi.path = target;
            }
            return true;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to toggle disable for {}", entry, e);
            return false;
        }
    }

    private Path resolveExistingPath(Path path, DiffEntry.ModInstance instance) {
        if (path == null) return null;
        if (Files.exists(path)) return path;
        String name = path.getFileName().toString();
        Path alt = name.endsWith(".disabled")
                ? path.resolveSibling(name.replaceFirst("\\.disabled$", ""))
                : path.resolveSibling(name + ".disabled");
        if (Files.exists(alt)) {
            if (instance != null) {
                instance.path = alt;
            }
            return alt;
        }
        return null;
    }

    private boolean revertEntry(DiffEntry entry) {
        if (entry.savedMods.isEmpty()) return false;
        if (isCancelAllRequested()) {
            resetProgress();
            return false;
        }
        List<DownloadResult> downloads = new ArrayList<DownloadResult>();
        Set<Path> keepFinalPaths = new HashSet<Path>();
        try {
            Files.createDirectories(tmpDownloadsFolder);
            for (DiffEntry.ModInstance saved : entry.savedMods) {
                if (isCancelRequestedFor(entry)) {
                    consumeSingleCancel(); // RESET THE FLAG!
                    cleanupDownloads(downloads);
                    return false;
                }
                DownloadResult result = downloadSavedFile(entry, saved, tmpDownloadsFolder);
                if (result == null) {
                    consumeSingleCancel(); // RESET THE FLAG!
                    cleanupDownloads(downloads);
                    return false;
                }
                downloads.add(result);
                if (result.finalPath != null) {
                    keepFinalPaths.add(result.finalPath.toAbsolutePath().normalize());
                }
            }
            if (isCancelRequestedFor(entry)) {
                consumeSingleCancel(); // RESET THE FLAG!
                cleanupDownloads(downloads);
                return false;
            }
            if (!deletePaths(entry.currentPaths(), keepFinalPaths, entry)) {
                consumeSingleCancel(); // RESET THE FLAG!
                cleanupDownloads(downloads);
                return false;
            }
            if (isCancelRequestedFor(entry)) {
                consumeSingleCancel(); // RESET THE FLAG!
                cleanupDownloads(downloads);
                return false;
            }
            if (!placeDownloads(downloads, entry)) {
                consumeSingleCancel(); // RESET THE FLAG!
                cleanupDownloads(downloads);
                return false;
            }
            entry.resolved = true;
            entry.resolvedBy = SectionAction.REVERT;
            resetProgress();
            return true;
        } catch (Exception e) {
            if (!isCancelRequestedFor(entry)) {
                CrashAssistantApp.LOGGER.error("Failed to revert entry", e);
            }
            consumeSingleCancel(); // RESET THE FLAG!
            cleanupDownloads(downloads);
            resetProgress();
            return false;
        }
    }

    private boolean restoreEntry(DiffEntry entry) {
        if (entry.savedMods.isEmpty()) return false;
        if (isCancelAllRequested()) {
            resetProgress();
            return false;
        }
        List<DownloadResult> downloads = new ArrayList<DownloadResult>();
        Set<Path> keepFinalPaths = new HashSet<Path>();
        try {
            Files.createDirectories(tmpDownloadsFolder);
            for (DiffEntry.ModInstance saved : entry.savedMods) {
                DownloadResult result = downloadSavedFile(entry, saved, tmpDownloadsFolder);
                if (result == null) {
                    consumeSingleCancel(); // RESET THE FLAG!
                    cleanupDownloads(downloads);
                    return false;
                }
                downloads.add(result);
                if (result.finalPath != null) {
                    keepFinalPaths.add(result.finalPath.toAbsolutePath().normalize());
                }
            }
            if (isCancelRequestedFor(entry)) {
                consumeSingleCancel(); // RESET THE FLAG!
                cleanupDownloads(downloads);
                return false;
            }
            if (!placeDownloads(downloads, entry)) {
                consumeSingleCancel(); // RESET THE FLAG!
                cleanupDownloads(downloads);
                return false;
            }
            entry.resolved = true;
            resetProgress();
            return true;
        } catch (Exception e) {
            if (!isCancelRequestedFor(entry)) {
                CrashAssistantApp.LOGGER.error("Failed to restore entry", e);
            }
            consumeSingleCancel(); // RESET THE FLAG!
            cleanupDownloads(downloads);
            resetProgress();
            return false;
        }
    }

    private DownloadResult downloadSavedFile(DiffEntry entry, DiffEntry.ModInstance saved, Path stagingDir) throws Exception {
        if (saved == null) return null;

        // Ensure we start without interrupt flags
        clearInterruptFlag();

        // Assign active download references IMMEDIATELY.
        this.activeDownloadEntry = entry;
        this.activeDownloadConnection = null; // Will be set once created
        this.activeDownloadStream = null;

        if (isCancelRequestedFor(entry)) {
            consumeSingleCancel(); // RESET FLAG
            return null;
        }

        Files.createDirectories(stagingDir);
        CurseForge.FingerprintMatch cf = saved.curseMatch;
        Modrinth.VersionFileInfo mr = saved.modrinthMatch;
        String targetFileName = saved.path != null && saved.path.getFileName() != null
                ? saved.path.getFileName().toString()
                : saved.fileName();
        if (cf != null && cf.fileName != null) targetFileName = cf.fileName;
        else if (mr != null && mr.fileName != null) targetFileName = mr.fileName;

        Path finalDir = saved.path != null && saved.path.getParent() != null
                ? saved.path.getParent()
                : ModListUtils.MODS_FOLDER;
        Path finalPath = finalDir.resolve(targetFileName);
        Path disabledPath = finalPath.resolveSibling(finalPath.getFileName().toString() + ".disabled");

        Path existing = findExistingMatching(saved, finalPath, disabledPath);
        if (existing != null) {
            if (existing.getFileName().toString().endsWith(".disabled")) {
                try {
                    Files.move(existing, finalPath, StandardCopyOption.REPLACE_EXISTING);
                    existing = finalPath;
                } catch (Exception e) {
                    CrashAssistantApp.LOGGER.error("Failed to rename disabled file {}", existing, e);
                }
            }
            return new DownloadResult(saved, null, targetFileName, existing, true);
        }

        String cfUrl = (cf != null && cf.hasDownload()) ? cf.downloadUrl : null;
        String mrUrl = (mr != null && mr.downloadUrl != null) ? mr.downloadUrl : null;

        Path stagedTarget = stagingDir.resolve(targetFileName);
        cleanupPartialDownload(stagedTarget);

        if (cfUrl == null && mrUrl == null) {
            if (cf != null) {
                CurseForge.SlugInfo slugInfo = CurseForge.resolveSlug(cf.modId);
                String slug = slugInfo != null ? slugInfo.slug : null;
                String pageUrl = slug == null ? null : "https://www.curseforge.com/minecraft/mc-mods/" + slug + "/files/" + cf.fileId;
                final String expectedFileName = targetFileName;
                final Path expectedDir = stagingDir;
                final String expectedPageUrl = pageUrl;
                final boolean[] ok = new boolean[]{false};
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                final ManualDownloadDialog[] dialogRef = new ManualDownloadDialog[1];

                SwingUtilities.invokeLater(() -> {
                    ManualDownloadDialog dlg = new ManualDownloadDialog(
                            ModListDiffDialog.this,
                            expectedFileName,
                            expectedDir,
                            expectedPageUrl,
                            buildHashSet(saved.curseHash),
                            buildHashSet(saved.modrinthHash)
                    );
                    dialogRef[0] = dlg;
                    ok[0] = dlg.awaitResult();
                    latch.countDown();
                });

                // Allow interrupt while waiting for manual download
                try {
                    // Poll for cancel while waiting for the latch
                    while (!latch.await(100, TimeUnit.MILLISECONDS)) {
                        if (isCancelRequestedFor(entry)) {
                            SwingUtilities.invokeLater(() -> {
                                if (dialogRef[0] != null) dialogRef[0].dispose();
                            });
                            cleanupPartialDownload(stagedTarget);
                            consumeSingleCancel(); // RESET FLAG
                            return null;
                        }
                    }
                } catch (InterruptedException e) {
                    SwingUtilities.invokeLater(() -> {
                        if (dialogRef[0] != null) dialogRef[0].dispose();
                    });
                    cleanupPartialDownload(stagedTarget);
                    consumeSingleCancel(); // RESET FLAG
                    return null;
                }

                if (ok[0]) {
                    return new DownloadResult(saved, stagedTarget, targetFileName, finalPath, false);
                }
                return null;
            }
            if (cf == null && mr == null && cfReady && mrReady) {
                String unavailableMsg = LanguageProvider.get("gui.modlist_diff.unavailable_both")
                        .replace("$FILE$", targetFileName)
                        + " " + LanguageProvider.get("gui.modlist_diff.unavailable_legacy_hint");
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            this,
                            unavailableMsg,
                            LanguageProvider.get("gui.modlist_diff_dialog_name"),
                            JOptionPane.WARNING_MESSAGE
                    );
                    statusLabel.setText(LanguageProvider.get("gui.modlist_diff.footer.counts")
                            .replace("$ADDED$", Integer.toString(countSelected(addedEntries)))
                            .replace("$UPDATED$", Integer.toString(countSelected(updatedEntries)))
                            .replace("$REMOVED$", Integer.toString(countSelected(removedEntries))));
                    statusLabel.setForeground(new Color(70, 70, 70));
                });
                return null;
            }
            addWarning(LanguageProvider.get("gui.modlist_diff.wait_for_fetch"));
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    LanguageProvider.get("gui.modlist_diff.wait_for_fetch"),
                    LanguageProvider.get("gui.modlist_diff_dialog_name"),
                    JOptionPane.WARNING_MESSAGE
            ));
            return null;
        }

        // Attempt download with fallback
        boolean tryCf = (cfUrl != null);
        boolean tryMr = (mrUrl != null);

        String firstUrl = null;
        String secondUrl = null;
        boolean firstIsMr = false;

        if (preferModrinth && tryMr) {
            firstUrl = mrUrl;
            firstIsMr = true;
            if (tryCf) secondUrl = cfUrl;
        } else if (tryCf) {
            firstUrl = cfUrl;
            firstIsMr = false;
            if (tryMr) secondUrl = mrUrl;
        } else {
            firstUrl = mrUrl;
            firstIsMr = true;
        }

        try {
            boolean success = performDownload(firstUrl, stagedTarget, entry, targetFileName);
            if (!success) return null;
            return new DownloadResult(saved, stagedTarget, targetFileName, finalPath, false);
        } catch (Exception e) {
            if (isCancelRequestedFor(entry)) return null; // Immediate cancel handling

            if (secondUrl != null && isConnectionIssue(e)) {
                CrashAssistantApp.LOGGER.warn("Failed to download from {}, trying fallback to {}. Error: {}",
                        firstIsMr ? "Modrinth" : "CurseForge",
                        firstIsMr ? "CurseForge" : "Modrinth",
                        e.toString());

                cleanupPartialDownload(stagedTarget);

                try {
                    // Update preference immediately if we are forced to fallback.
                    // If CF failed with a connection issue, it's likely a systemic block/issue,
                    // so we should default to Modrinth for the rest of this batch to save time.
                    if (!firstIsMr) {
                        preferModrinth = true;
                    }

                    boolean success = performDownload(secondUrl, stagedTarget, entry, targetFileName);
                    if (!success) return null;

                    return new DownloadResult(saved, stagedTarget, targetFileName, finalPath, false);
                } catch (Exception ex) {
                    if (isCancelRequestedFor(entry)) return null; // Immediate cancel handling for fallback
                    throw ex; // Throw the fallback exception
                }
            }
            throw e; // Rethrow original if no fallback
        }
    }

    private boolean performDownload(String downloadUrl, Path stagedTarget, DiffEntry entry, String targetFileName) throws Exception {
        updateProgress(LanguageProvider.get("gui.modlist_diff.downloading").replace("$FILE$", targetFileName), true);

        // Asynchronous Connector Pattern
        java.net.URL url = new java.net.URL(downloadUrl);
        java.net.URLConnection conn = url.openConnection();
        java.net.HttpURLConnection httpConn = conn instanceof java.net.HttpURLConnection ? (java.net.HttpURLConnection) conn : null;

        if (httpConn != null) {
            CurseForge.authenticateDownload(httpConn);
            httpConn.setConnectTimeout(15000);
            httpConn.setReadTimeout(15000);
            this.activeDownloadConnection = httpConn; // Track explicitly
        }

        // Before we start blocking network I/O
        if (isCancelRequestedFor(entry) || Thread.currentThread().isInterrupted()) {
            abortRunningDownload(entry);
            consumeSingleCancel(); // RESET FLAG
            return false;
        }

        AtomicReference<java.io.InputStream> rawInRef = new AtomicReference<java.io.InputStream>();
        AtomicReference<Exception> connectionException = new AtomicReference<Exception>();
        CountDownLatch connectionLatch = new CountDownLatch(1);

        Thread connectorThread = new Thread(() -> {
            try {
                rawInRef.set(conn.getInputStream());
            } catch (Exception e) {
                connectionException.set(e);
            } finally {
                connectionLatch.countDown();
            }
        }, "modlist-downloader-connector");
        connectorThread.setDaemon(true);
        connectorThread.start();

        // Wait loop: Poll for cancellation every 100ms
        while (true) {
            try {
                if (connectionLatch.await(100, TimeUnit.MILLISECONDS)) {
                    break; // Connected or failed, exit loop
                }
            } catch (InterruptedException e) {
                // Main thread interrupted -> force cancel
                connectorThread.interrupt();
                abortRunningDownload(entry);
                consumeSingleCancel(); // RESET FLAG
                return false;
            }

            // Check our logic flag
            if (isCancelRequestedFor(entry)) {
                connectorThread.interrupt(); // Stop helper
                abortRunningDownload(entry); // Hard kill connection
                consumeSingleCancel(); // RESET FLAG
                return false;
            }
        }

        // Check connection result
        if (connectionException.get() != null) {
            // If exception occurred, maybe it was due to a cancel?
            if (isCancelRequestedFor(entry)) {
                abortRunningDownload(entry);
                consumeSingleCancel(); // RESET FLAG
                return false;
            }
            throw connectionException.get(); // Real error
        }

        java.io.InputStream rawIn = rawInRef.get();
        if (rawIn == null) {
            // Should theoretically not happen if exception is null, but safety first
            abortRunningDownload(entry);
            return false;
        }

        rememberActiveDownload(entry, rawIn, httpConn);

        // Even if we connected successfully, the user might have clicked cancel precisely
        // in the milliseconds between the latch release and this line.
        // If we proceed to read(), we will block and the cancel will be ignored until IO error.
        // We must check again here.
        if (isCancelRequestedFor(entry)) {
            abortRunningDownload(entry); // This closes the fresh rawIn stream
            consumeSingleCancel(); // RESET FLAG
            return false;
        }

        int contentLength = conn.getContentLength();

        try (java.io.InputStream in = rawIn;
             java.io.OutputStream out = java.nio.file.Files.newOutputStream(stagedTarget)) {
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while (true) {
                if (Thread.currentThread().isInterrupted() || isCancelRequestedFor(entry)) {
                    cleanupPartialDownload(stagedTarget);
                    consumeSingleCancel(); // RESET FLAG
                    return false;
                }
                read = in.read(buf);
                if (read == -1) break;
                out.write(buf, 0, read);
                total += read;
                if (contentLength > 0) {
                    int percent = (int) (total * 100 / contentLength);
                    updateProgress(LanguageProvider.get("gui.modlist_diff.downloading").replace("$FILE$", targetFileName) + " " + percent + "%", false, percent);
                }
            }
            if (isCancelRequestedFor(entry)) {
                cleanupPartialDownload(stagedTarget);
                consumeSingleCancel(); // RESET FLAG
                return false;
            }
        } finally {
            // Cleanup active state immediately upon completion/failure/cancel
            // to ensure the Next Operation in the queue doesn't inherit dirty state.
            clearActiveDownload(rawIn);
        }
        return true;
    }

    private Path findExistingMatching(DiffEntry.ModInstance saved, Path finalPath, Path disabledPath) {
        try {
            if (Files.exists(finalPath) && fingerprintMatches(saved, finalPath)) {
                return finalPath;
            }
            if (Files.exists(disabledPath) && fingerprintMatches(saved, disabledPath)) {
                return disabledPath;
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to check existing file fingerprint for {}", finalPath, e);
        }
        return null;
    }

    private boolean fingerprintMatches(DiffEntry.ModInstance saved, Path candidate) {
        try {
            ModFingerprinter.IdentificationResult fp = ModFingerprinter.identify(candidate);
            boolean cfOk = saved.curseHash != null && saved.curseHash.equals(fp.getCurseForgeHash());
            boolean mrOk = saved.modrinthHash != null && saved.modrinthHash.equals(fp.getModrinthHash());
            return cfOk || mrOk;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to fingerprint candidate {}", candidate, e);
            return false;
        }
    }

    private boolean placeDownloads(List<DownloadResult> downloads, DiffEntry entry) throws Exception {
        for (DownloadResult dl : downloads) {
            if (isCancelRequestedFor(entry)) {
                consumeSingleCancel(); // RESET FLAG
                return false;
            }
            if (dl == null) continue;
            if (dl.alreadyPresent) {
                if (dl.finalPath != null) {
                    dl.source.path = dl.finalPath;
                }
                continue;
            }
            if (dl.stagedPath == null) continue;
            Path finalTarget = dl.finalPath != null ? dl.finalPath : dl.stagedPath;
            Files.createDirectories(finalTarget.getParent());
            Files.move(dl.stagedPath, finalTarget, StandardCopyOption.REPLACE_EXISTING);
            dl.source.path = finalTarget;
        }
        return true;
    }

    private void cleanupDownloads(List<DownloadResult> downloads) {
        for (DownloadResult dl : downloads) {
            if (dl == null || dl.stagedPath == null) continue;
            cleanupPartialDownload(dl.stagedPath);
        }
    }

    private boolean deletePaths(List<Path> paths, Set<Path> keep, DiffEntry entry) {
        Set<Path> normalizedKeep = new HashSet<Path>();
        for (Path k : keep) {
            if (k != null) normalizedKeep.add(k.toAbsolutePath().normalize());
        }
        for (Path p : paths) {
            if (isCancelRequestedFor(entry)) {
                consumeSingleCancel(); // RESET FLAG
                return false;
            }
            if (p == null) continue;
            Path np = p.toAbsolutePath().normalize();
            if (normalizedKeep.contains(np)) continue;
            try {
                Files.deleteIfExists(p);
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Failed to delete {}", p, e);
            }
        }
        return true;
    }

    private void cleanupPartialDownload(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("Failed to delete partial download {}", target, e);
        }
    }

    private HashSet<Long> buildHashSet(Long value) {
        HashSet<Long> set = new HashSet<Long>();
        if (value != null) set.add(value);
        return set;
    }

    private HashSet<String> buildHashSet(String value) {
        HashSet<String> set = new HashSet<String>();
        if (value != null) set.add(value);
        return set;
    }

    private static class DownloadResult {
        final DiffEntry.ModInstance source;
        final Path stagedPath;
        final String finalFileName;
        final Path finalPath;
        final boolean alreadyPresent;

        DownloadResult(DiffEntry.ModInstance source, Path stagedPath, String finalFileName, Path finalPath, boolean alreadyPresent) {
            this.source = source;
            this.stagedPath = stagedPath;
            this.finalFileName = finalFileName;
            this.finalPath = finalPath;
            this.alreadyPresent = alreadyPresent;
        }
    }

    private void openFolder(DiffEntry entry) {
        Path p = null;
        List<Path> current = entry.currentPaths();
        if (!current.isEmpty()) {
            p = current.get(0);
        } else {
            List<Path> saved = entry.savedPaths();
            if (!saved.isEmpty()) {
                p = saved.get(0);
            }
        }
        if (p == null) return;
        try {
            if (!Files.exists(p)) return;
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", p.toAbsolutePath().toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", p.toAbsolutePath().toString()).start();
            } else {
                Path dir = Files.isDirectory(p) ? p : p.getParent();
                if (dir != null) new ProcessBuilder("xdg-open", dir.toAbsolutePath().toString()).start();
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to reveal {}", p, e);
        }
    }

    void openCurseForgeProject(DiffEntry entry) {
        DiffEntry.ModInstance mi = entry.anyCurseMatchInstance();
        CurseForge.FingerprintMatch match = mi != null ? mi.curseMatch : null;
        if (match == null) return;
        try {
            String url = null;
            CurseForge.SlugInfo slugInfo = CurseForge.resolveSlug(match.modId);
            if (slugInfo != null) {
                if (slugInfo.websiteUrl != null && !slugInfo.websiteUrl.isEmpty()) {
                    url = slugInfo.websiteUrl;
                } else if (slugInfo.slug != null && !slugInfo.slug.isEmpty()) {
                    url = "https://www.curseforge.com/minecraft/mc-mods/" + slugInfo.slug;
                }
            }
            if (url == null) {
                url = "https://www.curseforge.com/minecraft/mc-mods/" + match.modId;
            }
            if (Desktop.isDesktopSupported()) {
                LinksHelper.browse(new URI(url));
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open CurseForge project", e);
        }
    }

    void openModrinthProject(DiffEntry entry) {
        DiffEntry.ModInstance mi = entry.anyModrinthMatchInstance();
        Modrinth.VersionFileInfo info = mi != null ? mi.modrinthMatch : null;
        if (info == null || info.projectUrl == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                LinksHelper.browse(new URI(info.projectUrl));
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to open Modrinth project", e);
        }
    }

    private static ImageIcon loadIcon(String resourcePath) {
        try {
            java.net.URL url = ModListDiffDialog.class.getResource(resourcePath);
            if (url == null) return null;
            return new ImageIcon(url);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to load icon {}", resourcePath, e);
            return null;
        }
    }

    ImageIcon getCfIcon() {
        return CF_ICON;
    }

    ImageIcon getMrIcon() {
        return MR_ICON;
    }

    boolean isCfReady() {
        return cfReady;
    }

    boolean isMrReady() {
        return mrReady;
    }

    boolean isEntryActive(DiffEntry entry) {
        if (entry == null) return false;
        if (entry.resolved) return false;
        if (cancelAllRequested || isCancelInProgressFor(entry)) return false;
        return entry.revertState != ActionState.RUNNING && entry.restoreState != ActionState.RUNNING;
    }

    boolean isActionEnabled(DiffEntry entry, SectionAction action) {
        if (entry == null || action == null) return false;
        if (cancelAllRequested || isCancelInProgressFor(entry)) return false;
        if (!isEntryActive(entry)) return false;
        if (action == SectionAction.REVERT) return entry.revertState == ActionState.IDLE;
        if (action == SectionAction.RESTORE) return entry.restoreState == ActionState.IDLE;
        return true;
    }

    String getRowEnableLabel() {
        String label = LanguageProvider.get("gui.files_remover.enable_selected");
        int space = label.indexOf(' ');
        if (space > 0) {
            label = label.substring(0, space);
        }
        return label;
    }

    String getActionLabel(DiffEntry entry, SectionAction action, String defaultLabel) {
        if (entry.resolved && entry.resolvedBy != null && action != entry.resolvedBy) {
            return "";
        }
        switch (action) {
            case REVERT:
                if (entry.revertState == ActionState.RUNNING)
                    return LanguageProvider.get("gui.modlist_diff.actions.reverting");
                if (entry.revertState == ActionState.DONE)
                    return LanguageProvider.get("gui.modlist_diff.actions.reverted");
                break;
            case RESTORE:
                if (entry.restoreState == ActionState.RUNNING)
                    return LanguageProvider.get("gui.modlist_diff.actions.restoring");
                if (entry.restoreState == ActionState.DONE)
                    return LanguageProvider.get("gui.modlist_diff.actions.restored");
                break;
            case DISABLE:
                if (isDisabledEntry(entry)) {
                    return getRowEnableLabel();
                }
                break;
            case REMOVE:
                if (entry.resolved && entry.resolvedBy != null && entry.resolvedBy != SectionAction.REMOVE) return "";
                if (entry.removedByAction) {
                    return LanguageProvider.get("gui.modlist_diff.section.removed");
                }
                break;
            case SHOW_FOLDER:
                return LanguageProvider.get("gui.show");
            default:
                break;
        }
        return defaultLabel;
    }

    JPanel wrapTop(JComponent comp) {
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                Dimension d = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, d.height);
            }
        };
        wrapper.add(comp, BorderLayout.NORTH);
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setAlignmentY(Component.TOP_ALIGNMENT);
        return wrapper;
    }

    private Dimension calculateMinSize() {
        int maxTableWidth = 0;
        for (SectionPanel sp : sectionPanels.values()) {
            maxTableWidth = Math.max(maxTableWidth, sp.getComponent().getPreferredSize().width);
        }

        int scrollbar = 32;
        int padding = 100; // borders/margins
        int minWidth = Math.max(maxTableWidth + scrollbar + padding, 600);
        return new Dimension(minWidth, 300);
    }

    int measureHeaderWidth(String text) {
        JLabel lbl = new JLabel(text);
        FontMetrics fm = lbl.getFontMetrics(lbl.getFont());
        return fm.stringWidth(text) + 24; // include default insets
    }
}
