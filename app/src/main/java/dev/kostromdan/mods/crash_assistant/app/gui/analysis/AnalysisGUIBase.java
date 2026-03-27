package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.app.gui.FilesRemover;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AnalysisGUIBase {
    protected static final List<Process> runningProcesses = Collections.synchronizedList(new java.util.ArrayList<>());
    protected static final Color ERROR_COLOR = Color.RED;
    protected static final Color NORMAL_COLOR = Color.BLACK;
    protected static final Color MOD_COLOR = new Color(0, 0, 255); // Blue
    protected static volatile boolean isCancelled = false;
    protected static ExecutorService executor;
    protected JDialog dialog;
    protected JTextPane textPane;
    protected JLabel statusLabel;
    protected JLabel currentJarLabel;
    protected JProgressBar progressBar;
    protected JPanel headerPanel;
    protected final java.util.LinkedHashSet<String> detectedModJarsForRemoval = new java.util.LinkedHashSet<>();

    public AnalysisGUIBase(JFrame parent, String title, String headerText) {
        dialog = new JDialog(parent, title + " (" + LanguageProvider.get("gui.window_name") + ")", true);
        dialog.setLayout(new BorderLayout());
        CrashAssistantGUI.setUpIcon(dialog);

        JLabel headerLabel = new JLabel("<html>" + headerText.replaceAll("\n", "<br>") + "<br>&nbsp;</html>");
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel(LanguageProvider.get("gui.analysis.analyzing_mods"));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        currentJarLabel = new JLabel(LanguageProvider.get("gui.analysis.current_mod") + " " + LanguageProvider.get("gui.analysis.none"));
        currentJarLabel.setHorizontalAlignment(SwingConstants.LEFT);
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(currentJarLabel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);

        headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerLabel, BorderLayout.NORTH);
        headerPanel.add(topPanel, BorderLayout.SOUTH);

        dialog.add(headerPanel, BorderLayout.NORTH);

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setCaretPosition(0);
        DefaultCaret caret = (DefaultCaret) textPane.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane scrollPane = new JScrollPane(textPane);
        dialog.add(scrollPane, BorderLayout.CENTER);

        dialog.setSize(900, 500);
        dialog.setLocationRelativeTo(parent);

        isCancelled = false;
        runningProcesses.clear();
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                isCancelled = true;
                executor.shutdownNow();
                synchronized (runningProcesses) {
                    for (Process p : runningProcesses) {
                        p.destroy();
                    }
                    runningProcesses.clear();
                }
            }
        });
    }

    protected void appendStyledText(String text, Color color) {
        StyledDocument doc = textPane.getStyledDocument();
        Style style = textPane.addStyle("Color Style", null);
        StyleConstants.setForeground(style, color);

        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
            CrashAssistantApp.LOGGER.error("Error appending styled text: ", e);
            try {
                doc.insertString(doc.getLength(), text, null);
            } catch (BadLocationException ignored) {
            }
        }
    }

    protected void addOkButton() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton detectedModsButton = new JButton(LanguageProvider.get("gui.analysis.detected_mods_files_remover_button"));
        detectedModsButton.setEnabled(!detectedModJarsForRemoval.isEmpty());
        detectedModsButton.addActionListener(e -> {
            Map<String, Path> map = buildDetectedModsMap();
            if (map.isEmpty()) {
                JOptionPane.showMessageDialog(
                        dialog,
                        LanguageProvider.get("gui.analysis.no_detected_mods"),
                        LanguageProvider.get("gui.files_remover.title"),
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }
            FilesRemover.showDialog(dialog, map, FilesRemover.Mode.JAR);
        });
        buttonPanel.add(detectedModsButton);

        String whyButtonKey = getWhyButtonTextKey();
        if (whyButtonKey != null) {
            JButton whyButton = new JButton(LanguageProvider.get(whyButtonKey));
            whyButton.addActionListener(e -> {
                String titleKey = getWhyDialogTitleKey();
                String bodyKey = getWhyDialogBodyKey();
                String title = titleKey != null ? LanguageProvider.get(titleKey) : "";
                String body = bodyKey != null ? LanguageProvider.get(bodyKey) : "";
                int width = getWhyDialogWidth();
                JEditorPane infoPane = CrashAssistantGUI.getEditorPane(body, true, width);
                JOptionPane.showMessageDialog(
                        dialog,
                        infoPane,
                        title,
                        JOptionPane.INFORMATION_MESSAGE
                );
            });
            buttonPanel.add(whyButton);
        }

        JButton okButton = new JButton(LanguageProvider.get("gui.ok"));
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.revalidate();
    }

    protected Map<String, Path> buildDetectedModsMap() {
        LinkedHashMap<String, Path> map = new LinkedHashMap<>();
        synchronized (detectedModJarsForRemoval) {
            for (String jar : detectedModJarsForRemoval) {
                if (jar == null) continue;
                map.put(jar, ModListUtils.MODS_FOLDER.resolve(jar));
            }
        }
        return map;
    }

    protected void registerDetectedModJar(String jarName) {
        if (jarName == null || jarName.isEmpty()) return;
        synchronized (detectedModJarsForRemoval) {
            detectedModJarsForRemoval.add(jarName);
        }
    }

    protected String getWhyButtonTextKey() {
        return null;
    }

    protected String getWhyDialogTitleKey() {
        return null;
    }

    protected String getWhyDialogBodyKey() {
        return null;
    }

    protected int getWhyDialogWidth() {
        return 500;
    }

    protected void addToHeaderCenter(Component component) {
        headerPanel.add(component, BorderLayout.CENTER);
        headerPanel.revalidate();
        headerPanel.repaint();
    }

    public void start() {
        new Thread(() -> {
            performAnalysis();
            if (!isCancelled) {
                SwingUtilities.invokeLater(() -> {
                    addOkButton();
                    statusLabel.setText(LanguageProvider.get("gui.analysis.analysis_complete"));
                    currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + LanguageProvider.get("gui.analysis.none"));
                });
            }
        }).start();
        dialog.setVisible(true);
    }

    protected abstract void performAnalysis();
}
