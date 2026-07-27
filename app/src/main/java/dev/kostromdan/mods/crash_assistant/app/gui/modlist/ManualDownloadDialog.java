package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.LinksHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.SwingEDT;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModFingerprinter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import javax.swing.TransferHandler.TransferSupport;

/**
 * Dialog asking the user to manually download a mod when direct links are not available.
 * Watches the default Downloads folder and supports drag-and-drop.
 */
public class ManualDownloadDialog extends JDialog {
    private final Path downloadsDir;
    private final Path targetDir;
    private final String expectedFileName;
    private final HashSet<Long> expectedCfHashes;
    private final HashSet<String> expectedMrHashes;
    private final String downloadPageUrl;
    private final JLabel statusLabel = new JLabel();
    private volatile boolean completed = false;
    private volatile boolean skipped = false;

    public ManualDownloadDialog(Window owner, String expectedFileName, Path targetDir, String downloadPageUrl,
                                HashSet<Long> expectedCfHashes, HashSet<String> expectedMrHashes) {
        super(owner, LanguageProvider.get("gui.modlist_diff.manual_download.title"), ModalityType.APPLICATION_MODAL);
        this.expectedFileName = expectedFileName;
        this.targetDir = targetDir;
        this.downloadPageUrl = downloadPageUrl;
        this.expectedCfHashes = expectedCfHashes;
        this.expectedMrHashes = expectedMrHashes;
        this.downloadsDir = Paths.get(System.getProperty("user.home", ""), "Downloads");

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                skipped = true;
                setVisible(false);
            }
        });

        buildUi();
        startWatcherThread();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTextArea message = new JTextArea(LanguageProvider.get("gui.modlist_diff.manual_download.message")
                .replace("$FILE$", expectedFileName));
        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setBackground(root.getBackground());
        message.setBorder(new EmptyBorder(0, 0, 8, 0));
        root.add(message, BorderLayout.NORTH);

        JPanel dropPanel = new JPanel(new BorderLayout());
        dropPanel.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
        dropPanel.setBackground(new Color(245, 245, 245));
        JLabel dropLabel = new JLabel(LanguageProvider.get("gui.modlist_diff.manual_download.drag_drop"), SwingConstants.CENTER);
        dropLabel.setBorder(new EmptyBorder(12, 12, 12, 12));
        dropPanel.add(dropLabel, BorderLayout.CENTER);
        dropPanel.setPreferredSize(new Dimension(360, 140));
        root.add(dropPanel, BorderLayout.CENTER);

        dropPanel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Transferable t = support.getTransferable();
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) return false;
                    Path candidate = files.get(0).toPath();
                    return handleCandidate(candidate, false);
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.error("Failed to process dropped file", ex);
                    return false;
                }
            }
        });

        statusLabel.setText(LanguageProvider.get("gui.modlist_diff.manual_download.waiting")
                .replace("$FILE$", expectedFileName));
        statusLabel.setBorder(new EmptyBorder(10, 0, 6, 0));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        if (downloadPageUrl != null) {
            JButton openPage = new JButton(LanguageProvider.get("gui.modlist_diff.manual_download.open_page"));
            openPage.addActionListener(this::openPage);
            buttons.add(openPage);
        }
        JButton skip = new JButton(LanguageProvider.get("gui.modlist_diff.manual_download.skip"));
        skip.addActionListener(e -> {
            skipped = true;
            completed = false;
            setVisible(false);
        });
        buttons.add(skip);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void openPage(ActionEvent e) {
        try {
            if (Desktop.isDesktopSupported()) {
                LinksHelper.browse(URI.create(downloadPageUrl));
            }
        } catch (Exception ex) {
            CrashAssistantApp.LOGGER.error("Failed to open download page {}", downloadPageUrl, ex);
        }
    }

    private void startWatcherThread() {
        Thread t = new Thread(() -> {
            while (!completed && !skipped) {
                try {
                    if (SwingEDT.callAndWait(this::isVisible)) {
                        checkDownloadsDir();
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }, "manual-download-watcher");
        t.setDaemon(true);
        t.start();
    }

    private void checkDownloadsDir() {
        try {
            if (downloadsDir != null) {
                Path candidate = downloadsDir.resolve(expectedFileName);
                if (Files.exists(candidate)) {
                    if (handleCandidate(candidate, true)) {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("Failed to monitor downloads folder", e);
        }
    }

    private boolean tryMoveCandidate(Path candidate) {
        try {
            if (!Files.exists(candidate)) return false;
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(expectedFileName);
            Files.move(candidate, target, StandardCopyOption.REPLACE_EXISTING);
            completed = true;
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(LanguageProvider.get("gui.modlist_diff.manual_download.done"));
                setVisible(false);
            });
            return true;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to move downloaded file {}", candidate, e);
            return false;
        }
    }

    private ModFingerprinter.IdentificationResult fingerprint(Path candidate) {
        try {
            return ModFingerprinter.identify(candidate);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to fingerprint {}", candidate, e);
            return null;
        }
    }

    private boolean hashesMatch(ModFingerprinter.IdentificationResult fp) {
        if (fp == null) return false;
        boolean cfOk = expectedCfHashes != null && expectedCfHashes.contains(fp.getCurseForgeHash());
        boolean mrOk = expectedMrHashes != null && expectedMrHashes.contains(fp.getModrinthHash());
        return cfOk || mrOk;
    }

    private boolean handleCandidate(Path candidate, boolean fromWatcher) {
        try {
            if (!Files.exists(candidate)) return false;
            boolean nameMatches = candidate.getFileName().toString().equals(expectedFileName);
            ModFingerprinter.IdentificationResult fp = fingerprint(candidate);
            boolean hashOk = hashesMatch(fp);

            if (hashOk) {
                return tryMoveCandidate(candidate);
            }

            // hash mismatch
            if (!nameMatches && fromWatcher) {
                return false; // silent ignore for watcher on completely unrelated files
            }

            String msg = LanguageProvider.get("gui.modlist_diff.manual_download.hash_mismatch_warning")
                    .replace("$FILE$", candidate.getFileName().toString())
                    .replace("$EXPECTED$", expectedFileName);
            int choice = SwingEDT.callAndWait(() ->
                    JOptionPane.showConfirmDialog(
                            this,
                            msg,
                            LanguageProvider.get("gui.modlist_diff_dialog_name"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    )
            );
            if (choice == JOptionPane.YES_OPTION) {
                return tryMoveCandidate(candidate);
            }
            return false;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to handle candidate {}", candidate, e);
            return false;
        }
    }

    /**
     * @return true when file obtained and moved, false if skipped or closed.
     */
    public boolean awaitResult() {
        setVisible(true);
        return completed && !skipped;
    }
}
