package dev.kostromdan.mods.crash_assistant.app.gui.scripts_ide;

import com.formdev.flatlaf.FlatLaf;
import dev.kostromdan.mods.crash_assistant.app.gui.ControlPanel;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogComparator;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogsList;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.scripts.Analysis;
import dev.kostromdan.mods.crash_assistant.app.scripts.AnalysisScriptManager;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import dev.kostromdan.mods.crash_assistant.app.utils.ThemeUtils;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.AbstractScriptManager;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.permissions.Permissions;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import java.awt.event.ActionEvent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptsIDE {

    private JFrame frame;
    private File currentScriptFile;
    private File defaultScriptsDir;
    private JTextPane editorArea;
    private Highlighter highlighter;
    private Highlighter.HighlightPainter errorPainter;
    private boolean isDirty = false;

    private List<Log> originalLogsList;
    private File tempSettingsDir;
    private File tempIdeLogsDir;
    private File tempIdeScriptsDir;

    private Set<KnownCrashReason> originalShownCrashReasons = new HashSet<>();
    private Set<KnownCrashReasonMessage> originalCrashReasonMessages = new HashSet<>();
    private Map<Log, List<ScriptWarning>> originalWarnings = new HashMap<>();

    private JTable logsTable;
    private DefaultTableModel logsTableModel;
    private Thread runThread;
    private Thread tailerThread;
    
    // --- Undo/Redo system (snapshot-based, avoids Swing UndoManager stale-view bug JDK-8061830) ---
    private final List<String> undoTextStack = new ArrayList<>();
    private final List<Integer> undoCaretStack = new ArrayList<>();
    private final List<String> redoTextStack = new ArrayList<>();
    private final List<Integer> redoCaretStack = new ArrayList<>();
    private boolean undoRedoInProgress = false;
    private static final int MAX_UNDO = 200;
    // snapshotText: the document text at the last committed snapshot point.
    // On the first edit in a burst, this is captured as the "before" state.
    private String snapshotText = "";
    private String pendingUndoText = null;
    private int pendingUndoCaret = 0;
    private final javax.swing.Timer undoCoalesceTimer = new javax.swing.Timer(300, ev -> commitPendingUndo());
    { undoCoalesceTimer.setRepeats(false); }

    /** Commits any pending undo state to the stack immediately. */
    private void commitPendingUndo() {
        undoCoalesceTimer.stop();
        if (pendingUndoText != null) {
            if (undoTextStack.isEmpty() || !undoTextStack.get(undoTextStack.size() - 1).equals(pendingUndoText)) {
                undoTextStack.add(pendingUndoText);
                undoCaretStack.add(pendingUndoCaret);
                if (undoTextStack.size() > MAX_UNDO) { undoTextStack.remove(0); undoCaretStack.remove(0); }
            }
            pendingUndoText = null;
        }
    }

    private void clearUndoHistory() {
        undoCoalesceTimer.stop();
        undoTextStack.clear();
        undoCaretStack.clear();
        redoTextStack.clear();
        redoCaretStack.clear();
        pendingUndoText = null;
        try {
            snapshotText = editorArea.getDocument().getText(0, editorArea.getDocument().getLength());
        } catch (Exception ex) { snapshotText = ""; }
    }
    
    private final File crashAssistantLogFile = new File("logs/crash_assistant/crash_assistant_app.log");
    private long logFilePointer = 0;
    private boolean dontShowEmptyLogsWarning = false;
    private static boolean isIdeRunning = false;

    public static boolean isIdeRunning() {
        return isIdeRunning;
    }

    private Color getColor(String[] keys, Color defaultColor) {
        for (String key : keys) {
            Color c = UIManager.getColor(key);
            if (c != null) return c;
        }
        return defaultColor;
    }

    public static void main(String[] args) {
        if (isIdeRunning) {
            return;
        }
        ThemeUtils.ensureThemesApplied();
        ControlPanel.stopMovingToTop = true;

        SwingUtilities.invokeLater(() -> {
            ScriptsIDE ide = new ScriptsIDE();
            ide.initSystemState();
            if (ide.promptInitialScript()) {
                ide.createAndShowGUI();
            } else {
                ide.restoreSystemState();
            }
        });
    }

    private void initSystemState() {
        isIdeRunning = true;
        defaultScriptsDir = new File("config/crash_assistant/scripts/log_analysis/");
        if (!defaultScriptsDir.exists()) {
            defaultScriptsDir.mkdirs();
        }

        tempSettingsDir = new File("temp_ide");
        if (!tempSettingsDir.exists()) {
            tempSettingsDir.mkdirs();
        }
        
        tempIdeLogsDir = new File("temp_ide_logs");
        if (!tempIdeLogsDir.exists()) {
            tempIdeLogsDir.mkdirs();
        }

        tempIdeScriptsDir = new File("config/crash_assistant/ide_temp_scripts");

        // Save original LogsList and set empty one
        originalLogsList = new ArrayList<>(LogsList.getLogs());
        LogsList.getLogs().clear();

        originalShownCrashReasons.addAll(KnownCrashReason.shownKnownCrashReasons);
        KnownCrashReason.shownKnownCrashReasons.clear();
        
        originalCrashReasonMessages.addAll(KnownCrashReasonMessage.getAllMessagesSnapshot());
        KnownCrashReasonMessage.getAllMessages().clear();
        
        synchronized (Analysis.getRegisteredWarnings()) {
            originalWarnings.putAll(Analysis.getRegisteredWarnings());
            Analysis.getRegisteredWarnings().clear();
        }
        
        // Scan currently present temp logs
        refreshIdeLogsFromDisk();
    }
    
    private void refreshIdeLogsFromDisk() {
        LogsList.getLogs().clear();
        if (tempIdeLogsDir.exists()) {
            File[] files = tempIdeLogsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        String name = f.getName();
                        LogType type = LogType.LOG;
                        try {
                            String baseType = name;
                            if (baseType.contains("-")) {
                                baseType = baseType.substring(0, baseType.indexOf('-'));
                            } else if (baseType.contains("_") && baseType.matches(".*_\\d+\\.log")) {
                                baseType = baseType.substring(0, baseType.lastIndexOf('_'));
                            }
                            type = LogType.valueOf(baseType.toUpperCase());
                        } catch (Exception e) {
                            String lowerName = name.toLowerCase();
                            for (LogType t : LogType.values()) {
                                if (lowerName.startsWith(t.name().toLowerCase()) || (t == LogType.CRASH_REPORT && lowerName.startsWith("crash"))) {
                                    type = t;
                                    break;
                                }
                            }
                        }
                        Log log = new Log(type, f.toPath());
                        log.getReader().readLogFileSafe();
                        LogsList.getLogs().add(log);
                    }
                }
            }
        }
        updateLogsTable();
    }
    
    private void updateLogsTable() {
        if (logsTableModel == null) return;
        logsTableModel.setRowCount(0);
        for (Log log : LogsList.getLogs()) {
            String displayName = log.getFileName();
            logsTableModel.addRow(new Object[]{displayName, log.getType()});
        }
    }

    private void restoreSystemState() {
        isIdeRunning = false;
        LogsList.getLogs().clear();
        LogsList.getLogs().addAll(originalLogsList);

        KnownCrashReason.shownKnownCrashReasons.clear();
        KnownCrashReason.shownKnownCrashReasons.addAll(originalShownCrashReasons);
        
        KnownCrashReasonMessage.getAllMessages().clear();
        KnownCrashReasonMessage.getAllMessages().addAll(originalCrashReasonMessages);
        
        Analysis.getRegisteredWarnings().clear();
        Analysis.getRegisteredWarnings().putAll(originalWarnings);
        
        boolean deleteLogsUrl = true;
        Object val = CrashAssistantLocalConfig.get("ide.delete_logs_on_exit");
        if (val instanceof Boolean) deleteLogsUrl = (Boolean) val;
        
        if (deleteLogsUrl) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(tempIdeLogsDir);
            } catch (IOException ignored) {}
        }
    }

    private boolean promptInitialScript() {
        Object[] options = {"Create New Script", "Open Existing Script", "Cancel"};
        int n = JOptionPane.showOptionDialog(null,
                "Select a script to start the IDE:",
                "Scripts IDE Initialization",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (n == 0) {
            JFileChooser fileChooser = new JFileChooser(defaultScriptsDir);
            fileChooser.setDialogTitle("Create New Script");
            fileChooser.setSelectedFile(new File(defaultScriptsDir, "script_name.jexl"));

            if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                currentScriptFile = fileChooser.getSelectedFile();
                if (!currentScriptFile.getName().endsWith(".jexl")) {
                    currentScriptFile = new File(currentScriptFile.getAbsolutePath() + ".jexl");
                }
                try {
                    currentScriptFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Failed to create file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                return true;
            }
        } else if (n == 1) {
            JFileChooser fileChooser = new JFileChooser(defaultScriptsDir);
            fileChooser.setDialogTitle("Open Existing Script");
            if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                currentScriptFile = fileChooser.getSelectedFile();
                return true;
            }
        }
        return false;
    }

    private void createAndShowGUI() {
        frame = new JFrame("Scripts IDE - " + currentScriptFile.getName());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isDirty) {
                    int result = JOptionPane.showConfirmDialog(frame, "You have unsaved changes. Save before closing?", "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        saveScript();
                    } else if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION) {
                        return; // Abort close
                    }
                }
                restoreSystemState();
                if (tailerThread != null && tailerThread.isAlive()) {
                    tailerThread.interrupt();
                }
                frame.dispose();
            }
        });

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setBounds((int)(screenSize.width * 0.1), (int)(screenSize.height * 0.1), 
                        (int)(screenSize.width * 0.8), (int)(screenSize.height * 0.8));

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open Another Script");
        openItem.addActionListener(e -> {
            if (isDirty) {
                int res = JOptionPane.showConfirmDialog(frame, "Save current script before opening another?", "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
                if (res == JOptionPane.YES_OPTION) saveScript();
                else if (res == JOptionPane.CANCEL_OPTION) return;
            }
            JFileChooser chooser = new JFileChooser(defaultScriptsDir);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                currentScriptFile = chooser.getSelectedFile();
                loadScript();
                frame.setTitle("Scripts IDE - " + currentScriptFile.getName());
            }
        });

        JMenuItem clearConfigItem = new JMenuItem("Clear Local Config");
        clearConfigItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(frame, "Wipe local config completely?", "Clear Local Config", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                CrashAssistantLocalConfig.clearAll();
                JOptionPane.showMessageDialog(frame, "Local config cleared!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JMenuItem createItem = new JMenuItem("Create New Script");
        createItem.addActionListener(e -> {
            if (isDirty) {
                int res = JOptionPane.showConfirmDialog(frame, "Save current script before creating another?", "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
                if (res == JOptionPane.YES_OPTION) saveScript();
                else if (res == JOptionPane.CANCEL_OPTION) return;
            }
            JFileChooser chooser = new JFileChooser(defaultScriptsDir);
            chooser.setDialogTitle("Create New Script");
            chooser.setSelectedFile(new File(defaultScriptsDir, "script_name.jexl"));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File newFile = chooser.getSelectedFile();
                if (!newFile.getName().endsWith(".jexl")) {
                    newFile = new File(newFile.getAbsolutePath() + ".jexl");
                }
                try {
                    newFile.createNewFile();
                    currentScriptFile = newFile;
                    loadScript();
                    frame.setTitle("Scripts IDE - " + currentScriptFile.getName());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Failed to create file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        fileMenu.add(createItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(clearConfigItem);
        menuBar.add(fileMenu);

        JButton btnSaveMenu = new JButton("Save");
        btnSaveMenu.setFocusPainted(false);
        btnSaveMenu.setContentAreaFilled(false);
        btnSaveMenu.setBorderPainted(false);
        btnSaveMenu.setMargin(new Insets(0, 10, 0, 10));
        btnSaveMenu.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSaveMenu.addActionListener(e -> saveScript());
        menuBar.add(btnSaveMenu);

        frame.setJMenuBar(menuBar);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        JButton btnRun = new JButton("▶ Run");
        btnRun.setForeground(new Color(0, 128, 0));
        btnRun.setFont(btnRun.getFont().deriveFont(Font.BOLD));

        JButton btnTerminate = new JButton("⏹ Terminate");
        btnTerminate.setForeground(Color.RED);
        btnTerminate.setEnabled(false);

        toolBar.add(btnRun);
        toolBar.addSeparator();
        toolBar.add(btnTerminate);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(new TitledBorder("Editor"));
        editorArea = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return false;
            }
            @Override
            public boolean getScrollableTracksViewportHeight() {
                return false;
            }
        };
        editorArea.setEditorKit(new NoWrapEditorKit());
        editorArea.setDocument(new JexlSyntaxDocument());
        editorArea.setFont(new Font("Monospaced", Font.PLAIN, 14));

        highlighter = editorArea.getHighlighter();
        errorPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 100, 100, 128));

        editorArea.getDocument().addDocumentListener(new DocumentListener() {
            private void onChange(DocumentEvent e) {
                if (e.getType() == DocumentEvent.EventType.CHANGE) return;
                highlighter.removeAllHighlights();
                isDirty = true;
                frame.setTitle("Scripts IDE - *" + currentScriptFile.getName());
                if (!undoRedoInProgress) {
                    // Clear redo immediately on any new user edit
                    redoTextStack.clear();
                    redoCaretStack.clear();
                    // On first edit in a burst, capture the pre-edit snapshot
                    if (pendingUndoText == null) {
                        pendingUndoText = snapshotText;
                        pendingUndoCaret = e.getOffset();
                    }
                    // Restart coalesce timer (groups rapid removes+inserts like paste)
                    undoCoalesceTimer.restart();
                    // Always update snapshotText to current document state
                    try {
                        snapshotText = editorArea.getDocument().getText(0, editorArea.getDocument().getLength());
                    } catch (Exception ex) { /* ignore */ }
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { onChange(e); }
            @Override public void removeUpdate(DocumentEvent e) { onChange(e); }
            @Override public void changedUpdate(DocumentEvent e) { onChange(e); }
        });

        InputMap im = editorArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = editorArea.getActionMap();

        im.put(KeyStroke.getKeyStroke("control Z"), "Undo");
        im.put(KeyStroke.getKeyStroke("control Y"), "Redo");
        im.put(KeyStroke.getKeyStroke("control shift Z"), "Redo");

        am.put("Undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Flush any pending edit burst into the undo stack first
                commitPendingUndo();
                if (undoTextStack.isEmpty()) return;
                undoRedoInProgress = true;
                try {
                    JexlSyntaxDocument doc = (JexlSyntaxDocument) editorArea.getDocument();
                    // Push current state to redo
                    String current = doc.getText(0, doc.getLength());
                    redoTextStack.add(current);
                    redoCaretStack.add(editorArea.getCaretPosition());
                    // Pop previous state from undo
                    String prev = undoTextStack.remove(undoTextStack.size() - 1);
                    int prevCaret = undoCaretStack.remove(undoCaretStack.size() - 1);
                    // Replace document content directly (bypasses auto-indent)
                    doc.setSuppressHighlighting(true);
                    doc.remove(0, doc.getLength());
                    doc.insertString(0, prev, null);
                    doc.setSuppressHighlighting(false);
                    editorArea.setCaretPosition(Math.min(prevCaret, prev.length()));
                    // Update snapshot to the restored text
                    snapshotText = prev;
                    SwingUtilities.invokeLater(() -> {
                        try { doc.refreshSyntaxHighlighting(); } catch(Exception ex) {}
                    });
                } catch (Exception ex) { /* ignore */ } finally {
                    undoRedoInProgress = false;
                }
            }
        });
        am.put("Redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commitPendingUndo();
                if (redoTextStack.isEmpty()) return;
                undoRedoInProgress = true;
                try {
                    JexlSyntaxDocument doc = (JexlSyntaxDocument) editorArea.getDocument();
                    // Push current state to undo
                    String current = doc.getText(0, doc.getLength());
                    undoTextStack.add(current);
                    undoCaretStack.add(editorArea.getCaretPosition());
                    // Pop next state from redo
                    String next = redoTextStack.remove(redoTextStack.size() - 1);
                    int nextCaret = redoCaretStack.remove(redoCaretStack.size() - 1);
                    // Replace document content directly (bypasses auto-indent)
                    doc.setSuppressHighlighting(true);
                    doc.remove(0, doc.getLength());
                    doc.insertString(0, next, null);
                    doc.setSuppressHighlighting(false);
                    editorArea.setCaretPosition(Math.min(nextCaret, next.length()));
                    // Update snapshot to the restored text
                    snapshotText = next;
                    SwingUtilities.invokeLater(() -> {
                        try { doc.refreshSyntaxHighlighting(); } catch(Exception ex) {}
                    });
                } catch (Exception ex) { /* ignore */ } finally {
                    undoRedoInProgress = false;
                }
            }
        });

        JScrollPane editorScroll = new JScrollPane(editorArea);
        LineNumberView lineNumberView = new LineNumberView(editorArea);
        editorScroll.setRowHeaderView(lineNumberView);
        editorScroll.getViewport().addChangeListener(e -> lineNumberView.repaint());
        editorPanel.add(editorScroll, BorderLayout.CENTER);

        JPanel logsPanel = new JPanel(new BorderLayout());
        logsPanel.setBorder(new TitledBorder("Logs"));

        String[] columnNames = {"File Name", "Log Type"};
        logsTableModel = new DefaultTableModel(null, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        logsTable = new JTable(logsTableModel);
        logsPanel.add(new JScrollPane(logsTable), BorderLayout.CENTER);

        JPanel logsControlPanel = new JPanel();
        logsControlPanel.setLayout(new BoxLayout(logsControlPanel, BoxLayout.Y_AXIS));
        logsControlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        LogType[] sortedTypes = LogType.values();
        Arrays.sort(sortedTypes, LogComparator::compareLogTypes);
        JComboBox<LogType> logTypeCombo = new JComboBox<>(sortedTypes);

        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addPanel.add(new JLabel("Type:"));
        addPanel.add(logTypeCombo);
        JButton btnAddClipboard = new JButton("Add from Clipboard");
        JButton btnAddFromGame = new JButton("Add from Game");
        addPanel.add(btnAddClipboard);
        addPanel.add(btnAddFromGame);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnOpenLog = new JButton("Open Selected Log");
        JButton btnDeleteLog = new JButton("Delete Selected Log");
        JButton btnClearLogs = new JButton("Clear All");
        actionPanel.add(btnOpenLog);
        actionPanel.add(btnDeleteLog);
        actionPanel.add(btnClearLogs);

        logsControlPanel.add(addPanel);
        logsControlPanel.add(actionPanel);
        
        JCheckBox cbDeleteLogs = new JCheckBox("Remove temporary logs folder after exiting IDE");
        
        boolean deleteLogsState = true;
        Object storedVal = CrashAssistantLocalConfig.get("ide.delete_logs_on_exit");
        if (storedVal instanceof Boolean) deleteLogsState = (Boolean) storedVal;
        
        cbDeleteLogs.setSelected(deleteLogsState);
        cbDeleteLogs.addActionListener(e -> CrashAssistantLocalConfig.set("ide.delete_logs_on_exit", cbDeleteLogs.isSelected()));
        
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        settingsPanel.add(cbDeleteLogs);
        
        logsControlPanel.add(settingsPanel);
        logsPanel.add(logsControlPanel, BorderLayout.SOUTH);
        Insets logsPanelInsets = logsPanel.getInsets();
        logsPanel.setMinimumSize(new Dimension(
                logsControlPanel.getPreferredSize().width + logsPanelInsets.left + logsPanelInsets.right,
                0));

        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBorder(new TitledBorder("Console"));
        JTextPane consoleArea = new JTextPane();
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        consoleArea.setEditable(false);
        consolePanel.add(new JScrollPane(consoleArea), BorderLayout.CENTER);

        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, logsPanel);
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, consolePanel);
        
        topSplitPane.setResizeWeight(0.75);
        mainSplitPane.setResizeWeight(0.66);

        frame.setLayout(new BorderLayout());
        frame.add(toolBar, BorderLayout.NORTH);
        frame.add(mainSplitPane, BorderLayout.CENTER);

        frame.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            loadScript();
            startLogTailer(consoleArea);
            updateLogsTable();
            topSplitPane.setDividerLocation(Math.min(
                    (int) (topSplitPane.getWidth() * 0.75),
                    topSplitPane.getMaximumDividerLocation()));
            mainSplitPane.setDividerLocation(0.666);
        });

        btnRun.addActionListener(e -> {
            btnRun.setEnabled(false);
            btnTerminate.setEnabled(true);
            
            saveScript();

            if (crashAssistantLogFile.exists()) {
                logFilePointer = crashAssistantLogFile.length();
            }

            if (LogsList.getLogs().isEmpty() && !dontShowEmptyLogsWarning) {
                JPanel panel = new JPanel(new BorderLayout(5, 5));
                JLabel label = new JLabel("<html>Logs list is empty. If your script is intended for log analysis,<br>" +
                        "it will find nothing. Please add logs (from clipboard or from game).</html>");
                JCheckBox checkBox = new JCheckBox("Don't show again until restart");
                panel.add(label, BorderLayout.CENTER);
                panel.add(checkBox, BorderLayout.SOUTH);

                int result = JOptionPane.showConfirmDialog(frame, panel, "Logs list is empty", 
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                
                if (checkBox.isSelected()) {
                    dontShowEmptyLogsWarning = true;
                }
                
                if (result != JOptionPane.OK_OPTION) {
                    btnRun.setEnabled(true);
                    btnTerminate.setEnabled(false);
                    return;
                }
            }

            runThread = new Thread(() -> {
                try {
                    SwingUtilities.invokeLater(() -> consoleArea.setText(""));
                    
                    // Cache ALL GUI states before the run
                    Set<KnownCrashReasonMessage> cachedMessages = new HashSet<>(KnownCrashReasonMessage.getAllMessagesSnapshot());
                    Set<KnownCrashReason> cachedShownReasons = new HashSet<>(KnownCrashReason.shownKnownCrashReasons);
                    Map<Log, List<ScriptWarning>> cachedWarnings = new HashMap<>();
                    synchronized (Analysis.getRegisteredWarnings()) {
                        cachedWarnings.putAll(Analysis.getRegisteredWarnings());
                    }
                    Set<String> cachedExecutedScripts = new HashSet<>(AbstractScriptManager.getExecutedScripts());
                    
                    // Reset to absolute zero for the isolated run
                    Analysis.getRegisteredWarnings().clear();
                    KnownCrashReasonMessage.getAllMessages().clear();
                    KnownCrashReason.shownKnownCrashReasons.clear();
                    AbstractScriptManager.clearExecutedScripts();
                    
                    // Clean cache to not measure Jexl engine init
                    Permissions.getEngine();
                    
                    if (tempIdeScriptsDir.exists()) {
                        File[] f = tempIdeScriptsDir.listFiles();
                        if (f != null) for(File t : f) t.delete();
                    } else {
                        tempIdeScriptsDir.mkdirs();
                    }
                    File tempScriptFile = new File(tempIdeScriptsDir, currentScriptFile.getName());
                    Files.copy(currentScriptFile.toPath(), tempScriptFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    
                    long startTime = System.currentTimeMillis();
                    AnalysisScriptManager ideManager = new AnalysisScriptManager() {
                        @Override protected Path getScriptsDir() { return tempIdeScriptsDir.toPath(); }
                    };
                    ideManager.runScripts();
                    
                    Map<Log, List<ScriptWarning>> warnings = Analysis.getRegisteredWarnings();
                    synchronized (warnings) {
                        for (Map.Entry<Log, List<ScriptWarning>> entry : warnings.entrySet()) {
                            Log log = entry.getKey();
                            for (ScriptWarning w : entry.getValue()) {
                                KnownCrashReason reason = new ScriptedAnalysis(log != null ? log.getType() : LogType.LOG, w);
                                KnownCrashReasonMessage.addCrashReasonMessage(new KnownCrashReasonMessage(log, reason));
                            }
                        }
                    }
                    long endTime = System.currentTimeMillis();
                    

                    Logger.info("Script {} executed successfully! (Time: {}ms)", currentScriptFile.getName(), endTime - startTime);
                    
                    if (!KnownCrashReasonMessage.getAllMessages().isEmpty()) {
                        CrashAssistantGUI.showKnownCrashReasonsWarnings();
                    }

                    // Restore ALL GUI states exactly as they were
                    KnownCrashReasonMessage.getAllMessages().clear();
                    KnownCrashReasonMessage.getAllMessages().addAll(cachedMessages);
                    
                    KnownCrashReason.shownKnownCrashReasons.clear();
                    KnownCrashReason.shownKnownCrashReasons.addAll(cachedShownReasons);
                    
                    synchronized (Analysis.getRegisteredWarnings()) {
                        Analysis.getRegisteredWarnings().clear();
                        Analysis.getRegisteredWarnings().putAll(cachedWarnings);
                    }
                    
                    AbstractScriptManager.clearExecutedScripts();
                    AbstractScriptManager.getExecutedScripts().addAll(cachedExecutedScripts);

                } catch (Exception ex) {
                    // Extract line number if JEXL failed
                    highlightErrorLine(ex.getMessage());
                } finally {
                    try {
                        org.apache.commons.io.FileUtils.deleteDirectory(tempIdeScriptsDir);
                    } catch (IOException ignored) {}
                    SwingUtilities.invokeLater(() -> {
                        btnRun.setEnabled(true);
                        btnTerminate.setEnabled(false);
                    });
                }
            });
            runThread.start();
        });

        btnTerminate.addActionListener(e -> {
            if (runThread != null && runThread.isAlive()) {
                runThread.interrupt();
            }
            btnTerminate.setEnabled(false);
            btnRun.setEnabled(true);
        });

        btnAddClipboard.addActionListener(e -> {
            try {
                java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    JOptionPane.showMessageDialog(frame, "Clipboard does not contain text data.", "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (data == null || data.isEmpty()) return;
                
                LogType selectedType = (LogType) logTypeCombo.getSelectedItem();
                String fileName = selectedType.name().toLowerCase() + "-" + System.currentTimeMillis() + ".log";
                File newFile = new File(tempIdeLogsDir, fileName);
                
                int count = 2;
                while (newFile.exists()) {
                    newFile = new File(tempIdeLogsDir, selectedType.name().toLowerCase() + "-" + System.currentTimeMillis() + "-" + count + ".log");
                    count++;
                }
                
                Files.write(newFile.toPath(), data.getBytes(StandardCharsets.UTF_8));
                
                Log newLog = new Log(selectedType, newFile.toPath());
                newLog.getReader().readLogFileSafe();
                LogsList.getLogs().add(newLog);
                updateLogsTable();
                
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to read clipboard: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnAddFromGame.addActionListener(e -> {
            try {
                for (Log gameLog : originalLogsList) {
                    if (gameLog != null && gameLog.getFile() != null && gameLog.getFile().exists()) {
                        LogType type = gameLog.getType();
                        String fileName = type.name().toLowerCase() + "-" + System.currentTimeMillis() + ".log";
                        File newFile = new File(tempIdeLogsDir, fileName);
                        
                        int count = 2;
                        while (newFile.exists()) {
                            newFile = new File(tempIdeLogsDir, type.name().toLowerCase() + "-" + System.currentTimeMillis() + "-" + count + ".log");
                            count++;
                        }
                        
                        Files.copy(gameLog.getFile().toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        
                        Log newLog = new Log(type, newFile.toPath());
                        newLog.getReader().readLogFileSafe();
                        LogsList.getLogs().add(newLog);
                    }
                }
                updateLogsTable();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to copy logs from game: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnOpenLog.addActionListener(e -> {
            int row = logsTable.getSelectedRow();
            if (row != -1) {
                String fileName = (String) logsTableModel.getValueAt(row, 0);
                File f = new File(tempIdeLogsDir, fileName);
                if (f.exists()) {
                    try {
                        Desktop.getDesktop().open(f);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, "Could not open file.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        btnDeleteLog.addActionListener(e -> {
            int row = logsTable.getSelectedRow();
            if (row != -1) {
                String fileName = (String) logsTableModel.getValueAt(row, 0);
                File f = new File(tempIdeLogsDir, fileName);
                if (f.exists()) f.delete();
                
                LogsList.getLogs().removeIf(log -> log.getFileName().equals(fileName));
                updateLogsTable();
            }
        });

        btnClearLogs.addActionListener(e -> {
            if (tempIdeLogsDir.exists()) {
                for (File f : tempIdeLogsDir.listFiles()) {
                    f.delete();
                }
            }
            LogsList.getLogs().clear();
            updateLogsTable();
        });
    }

    private void saveScript() {
        if (!isDirty) return;
        try {
            Files.write(currentScriptFile.toPath(), editorArea.getText().getBytes(StandardCharsets.UTF_8));
            isDirty = false;
            frame.setTitle("Scripts IDE - " + currentScriptFile.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Failed to save script: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadScript() {
        try {
            if (currentScriptFile.exists()) {
                String content = new String(Files.readAllBytes(currentScriptFile.toPath()), StandardCharsets.UTF_8);
                editorArea.setText(content);
                editorArea.setCaretPosition(0);
                // Clear undo history when loading a new script
                clearUndoHistory();
                
                isDirty = false;
                frame.setTitle("Scripts IDE - " + currentScriptFile.getName());
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Failed to read script: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void highlightErrorLine(String jexlErrorTrace) {
        if (jexlErrorTrace == null) return;
        Pattern pattern = Pattern.compile("@(\\d+):\\d+");
        Matcher matcher = pattern.matcher(jexlErrorTrace);
        if (matcher.find()) {
            try {
                int line = Integer.parseInt(matcher.group(1));
                SwingUtilities.invokeLater(() -> {
                    try {
                        Element root = editorArea.getDocument().getDefaultRootElement();
                        if (line > 0 && line <= root.getElementCount()) {
                            Element lineElement = root.getElement(line - 1);
                            int startOffset = lineElement.getStartOffset();
                            int endOffset = lineElement.getEndOffset();
                            highlighter.addHighlight(startOffset, endOffset, errorPainter);
                            editorArea.setCaretPosition(startOffset);
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                });
            } catch (Exception ex) {
                // Ignore
            }
        }
    }

    private void startLogTailer(JTextPane consoleArea) {
        boolean isDark = FlatLaf.isLafDark();
        
        AttributeSet attrError = StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, 
                getColor(new String[]{"Actions.Red", "Actions.RedComponent", "Editor.error.foreground"}, isDark ? new Color(255, 100, 100) : Color.RED));
        
        AttributeSet attrWarn = StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, 
                getColor(new String[]{"Actions.Yellow", "Actions.YellowComponent", "Actions.Orange", "Actions.OrangeComponent", "Editor.warning.foreground"}, isDark ? new Color(255, 200, 100) : Color.ORANGE));
        
        AttributeSet attrInfo = StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, 
                UIManager.getColor("TextArea.foreground"));
        
        AttributeSet attrNormal = StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, 
                UIManager.getColor("TextArea.foreground"));

        Thread tailer = new Thread(() -> {
            AttributeSet lastAttr = attrNormal;
            if (crashAssistantLogFile.exists()) {
                logFilePointer = crashAssistantLogFile.length();
            }

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (crashAssistantLogFile.exists()) {
                        long len = crashAssistantLogFile.length();
                        if (len < logFilePointer) {
                            logFilePointer = 0; // Rolled over
                        } else if (len > logFilePointer) {
                            try (RandomAccessFile raf = new RandomAccessFile(crashAssistantLogFile, "r")) {
                                raf.seek(logFilePointer);
                                String line;
                                while ((line = raf.readLine()) != null) {
                                    String utf8Line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                                    
                                    // Determine attribute for the current line
                                    AttributeSet currentAttr;
                                    if (utf8Line.contains("[ERROR]")) {
                                        currentAttr = attrError;
                                    } else if (utf8Line.contains("[WARN]") || utf8Line.contains("[WARNING]")) {
                                        currentAttr = attrWarn;
                                    } else if (utf8Line.contains("[INFO]")) {
                                        currentAttr = attrInfo;
                                    } else if (utf8Line.trim().startsWith("at ") || utf8Line.trim().startsWith("Caused by:") || utf8Line.trim().startsWith("...") || (utf8Line.contains("Exception") && !utf8Line.startsWith("["))) {
                                        // Continuation of an error/stacktrace
                                        currentAttr = attrError;
                                    } else if (utf8Line.startsWith("[")) {
                                        // New log entry header but not error/warn/info
                                        currentAttr = attrNormal;
                                    } else {
                                        // Use last known attribute for multiline continuations
                                        currentAttr = lastAttr;
                                    }
                                    lastAttr = currentAttr;
                                    AttributeSet finalAttr = currentAttr;

                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            StyledDocument doc = consoleArea.getStyledDocument();
                                            doc.insertString(doc.getLength(), utf8Line + "\n", finalAttr);
                                            consoleArea.setCaretPosition(doc.getLength());
                                        } catch (BadLocationException e) {
                                            // Ignore
                                        }
                                        highlightErrorLine(utf8Line);
                                    });
                                }
                                logFilePointer = raf.getFilePointer();
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    // Ignore
                }
            }
        });
        tailerThread = tailer;
        tailer.setDaemon(true);
        tailer.start();
    }
}

class JexlSyntaxDocument extends DefaultStyledDocument {
    private final StyleContext context = StyleContext.getDefaultStyleContext();
    private final AttributeSet attrKeyword;
    private final AttributeSet attrString;
    private final AttributeSet attrComment;
    private final AttributeSet attrNormal;
    private final AttributeSet attrVariable;
    private final javax.swing.Timer highlightTimer;
    private boolean suppressHighlighting = false;

    public void setSuppressHighlighting(boolean suppress) {
        this.suppressHighlighting = suppress;
    }

    public JexlSyntaxDocument() {
        boolean isDark = FlatLaf.isLafDark();
        
        Color keywordDefault = isDark ? new Color(204, 120, 50) : new Color(0, 0, 255);
        Color stringDefault = isDark ? new Color(106, 135, 89) : new Color(0, 128, 0);
        Color commentDefault = new Color(128, 128, 128);
        Color normalDefault = isDark ? new Color(169, 183, 198) : Color.BLACK;
        Color variableDefault = isDark ? new Color(103, 150, 186) : new Color(102, 14, 122);

        Color kwColor = getColor(new String[]{
            "Editor.keyword.foreground", 
            "ColorPalette.contrast", 
            "Keyword.foreground",
            "Actions.Blue", 
            "Actions.BlueComponent"
        }, keywordDefault);
        
        Color strColor = getColor(new String[]{
            "Editor.string.foreground", 
            "ColorPalette.hue3", 
            "String.foreground",
            "Actions.Green", 
            "Actions.GreenComponent"
        }, stringDefault);
        
        Color comColor = getColor(new String[]{
            "Editor.lineComment.foreground", 
            "ColorPalette.borderColor", 
            "Comment.foreground",
            "Actions.Grey", 
            "Actions.GreyComponent"
        }, commentDefault);
        
        Color normColor = getColor(new String[]{
            "EditorPane.foreground", 
            "TextArea.foreground", 
            "Label.foreground",
            "ColorPalette.textColor"
        }, normalDefault);

        Color varColor = getColor(new String[]{
            "Editor.variable.foreground",
            "ColorPalette.hue3",
            "ColorPalette.contrast",
            "ColorPalette.textColor"
        }, variableDefault);
        
        attrKeyword = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, kwColor);
        attrString = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, strColor);
        attrComment = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, comColor);
        attrNormal = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, normColor);
        attrVariable = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, varColor);
        
        highlightTimer = new javax.swing.Timer(300, e -> {
            try {
                refreshSyntaxHighlighting();
            } catch (BadLocationException ex) {
                // Ignore
            }
        });
        highlightTimer.setRepeats(false);
    }
    
    private Color getColor(String[] keys, Color defaultColor) {
        for (String key : keys) {
            Color c = UIManager.getColor(key);
            if (c != null) return c;
        }
        return defaultColor;
    }

    @Override
    public void insertString(int offset, String str, AttributeSet a) throws BadLocationException {
        if (suppressHighlighting) {
            // Raw mode: bypass auto-indent/tab/brace logic (used by undo/redo)
            super.insertString(offset, str, a);
            return;
        }
        if ("\t".equals(str)) {
            Element root = getDefaultRootElement();
            int index = root.getElementIndex(offset);
            Element line = root.getElement(index);
            int start = line.getStartOffset();
            String prefix = getText(start, offset - start);
            
            if (prefix.trim().isEmpty()) {
                String idealIndent = calculateIdealIndent(offset);
                if (prefix.length() < idealIndent.length()) {
                    super.remove(start, offset - start);
                    super.insertString(start, idealIndent, a);
                    return;
                }
            }
            str = "    ";
        }
        if ("\n".equals(str) || "\r\n".equals(str)) {
            Element root = getDefaultRootElement();
            int index = root.getElementIndex(offset);
            Element line = root.getElement(index);
            int start = line.getStartOffset();
            String prefix = getText(start, offset - start);
            
            String indent = "";
            Matcher m = Pattern.compile("^([ \\t]+)").matcher(prefix);
            if (m.find()) {
                indent = m.group(1);
            }
            if (prefix.trim().endsWith("{")) {
                indent += "    "; // 4 spaces for nested block
            }
            str += indent;
        } else if ("}".equals(str)) {
            Element root = getDefaultRootElement();
            int index = root.getElementIndex(offset);
            Element line = root.getElement(index);
            int start = line.getStartOffset();
            String lineText = getText(start, offset - start);
            
            if (lineText.trim().isEmpty()) {
                if (lineText.endsWith("    ")) {
                    super.remove(offset - 4, 4);
                    offset -= 4;
                } else if (lineText.endsWith("\t")) {
                    super.remove(offset - 1, 1);
                    offset -= 1;
                }
            }
        }
        super.insertString(offset, str, a);
        scheduleHighlighting();
    }

    private String calculateIdealIndent(int offset) throws BadLocationException {
        String text = getText(0, offset);
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == quote && (i == 0 || text.charAt(i - 1) != '\\')) inString = false;
            } else {
                if (c == '"' || c == '\'') { inString = true; quote = c; }
                else if (c == '{') depth++;
                else if (c == '}') depth--;
            }
        }
        if (depth < 0) depth = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("    ");
        return sb.toString();
    }

    @Override
    public void remove(int offs, int len) throws BadLocationException {
        super.remove(offs, len);
        scheduleHighlighting();
    }

    private void scheduleHighlighting() {
        if (suppressHighlighting) return;
        highlightTimer.restart();
    }

    public void refreshSyntaxHighlighting() throws BadLocationException {
        String text = getText(0, getLength());
        
        setCharacterAttributes(0, text.length(), attrNormal, true);
        
        Matcher m = Pattern.compile("\"([^\"\\n\\r]*)\"|'([^'\\n\\r]*)'").matcher(text);
        while (m.find()) setCharacterAttributes(m.start(), m.end() - m.start(), attrString, false);

        StringBuilder words = new StringBuilder("\\b(if|else|for|while|do|break|continue|return|function|var|let|const|null|true|false|new|empty|size|def");
        for (String key : Permissions.getClassMap().keySet()) {
            words.append("|").append(Pattern.quote(key));
        }
        words.append(")\\b");
        m = Pattern.compile(words.toString()).matcher(text);
        
        while (m.find()) {
            boolean isStyled = false;
            for (int i = m.start(); i < m.end(); i++) {
                Object color = getCharacterElement(i).getAttributes().getAttribute(StyleConstants.Foreground);
                if (attrString.getAttribute(StyleConstants.Foreground).equals(color)) {
                    isStyled = true;
                    break;
                }
            }
            if (!isStyled) {
                setCharacterAttributes(m.start(), m.end() - m.start(), attrKeyword, false);
            }
        }

        // Variable/Function highlighting (generic words)
        m = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b").matcher(text);
        while (m.find()) {
            boolean isStyled = false;
            for (int i = m.start(); i < m.end(); i++) {
                Object color = getCharacterElement(i).getAttributes().getAttribute(StyleConstants.Foreground);
                if (attrString.getAttribute(StyleConstants.Foreground).equals(color) || 
                    attrKeyword.getAttribute(StyleConstants.Foreground).equals(color)) {
                    isStyled = true;
                    break;
                }
            }
            if (!isStyled) {
                setCharacterAttributes(m.start(), m.end() - m.start(), attrVariable, false);
            }
        }
        
        m = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL).matcher(text);
        while (m.find()) setCharacterAttributes(m.start(), m.end() - m.start(), attrComment, false);
    }
}

class NoWrapEditorKit extends StyledEditorKit {
    @Override
    public ViewFactory getViewFactory() {
        return new NoWrapViewFactory();
    }

    private static class NoWrapViewFactory implements ViewFactory {
        @Override
        public View create(Element elem) {
            String kind = elem.getName();
            if (kind != null) {
                switch (kind) {
                    case AbstractDocument.ContentElementName:
                        // SafeLabelView catches stale-offset crashes in getBreakSpot
                        return new LabelView(elem) {
                            @Override
                            public int getBreakWeight(int axis, float pos, float len) {
                                try {
                                    return super.getBreakWeight(axis, pos, len);
                                } catch (Exception e) {
                                    return BadBreakWeight;
                                }
                            }
                        };
                    case AbstractDocument.ParagraphElementName:
                        // Use BoxView instead of ParagraphView to completely eliminate
                        // FlowView/LogicalView line-breaking machinery and avoid
                        // GlyphView.getBreakSpot stale offset crashes (JDK-8061830).
                        return new BoxView(elem, View.X_AXIS) {
                            @Override
                            public float getAlignment(int axis) {
                                return 0f; // left-align (BoxView defaults to 0.5 = center)
                            }
                        };
                    case AbstractDocument.SectionElementName:
                        return new BoxView(elem, View.Y_AXIS) {
                            @Override
                            public float getAlignment(int axis) {
                                return 0f; // top-align (BoxView defaults to 0.5 = center)
                            }
                        };
                    case StyleConstants.ComponentElementName:
                        return new ComponentView(elem);
                    case StyleConstants.IconElementName:
                        return new IconView(elem);
                }
            }
            return new LabelView(elem);
        }
    }
}

class LineNumberView extends JComponent {
    private final JTextPane textPane;
    private final int borderGap = 8;

    public LineNumberView(JTextPane textPane) {
        this.textPane = textPane;
        textPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { repaint(); }
            @Override public void removeUpdate(DocumentEvent e) { repaint(); }
            @Override public void changedUpdate(DocumentEvent e) { repaint(); }
        });
        textPane.addCaretListener(new CaretListener() {
            @Override public void caretUpdate(CaretEvent e) { repaint(); }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(getComponentWidth(), textPane.getHeight());
    }

    private int getComponentWidth() {
        int lineCount = textPane.getDocument().getDefaultRootElement().getElementCount();
        int maxDigits = Math.max(3, String.valueOf(lineCount).length());
        FontMetrics metrics = getFontMetrics(textPane.getFont());
        return maxDigits * metrics.charWidth('0') + borderGap * 2;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font font = textPane.getFont();
        g2d.setFont(font);
        FontMetrics metrics = g2d.getFontMetrics(font);
        int fontHeight = metrics.getHeight();
        int fontAscent = metrics.getAscent();

        Rectangle clip = g2d.getClipBounds();
        
        // Background
        Color bg = UIManager.getColor("Editor.gutter.background");
        if (bg == null) bg = textPane.getBackground().darker();
        g2d.setColor(bg);
        g2d.fillRect(clip.x, clip.y, clip.width, clip.height);

        // Right border line
        Color border = UIManager.getColor("Editor.gutter.borderColor");
        if (border == null) border = Color.GRAY;
        g2d.setColor(border);
        int componentWidth = getComponentWidth();
        g2d.drawLine(componentWidth - 1, clip.y, componentWidth - 1, clip.y + clip.height);

        Element root = textPane.getDocument().getDefaultRootElement();
        int lineCount = root.getElementCount();

        int currentLine = root.getElementIndex(textPane.getCaretPosition());

        Color normalColor = UIManager.getColor("Editor.gutter.foreground");
        if (normalColor == null) normalColor = Color.GRAY;
        
        Color currentColor = UIManager.getColor("Editor.gutter.selectionForeground");
        if (currentColor == null) currentColor = UIManager.getColor("Editor.foreground");
        if (currentColor == null) currentColor = textPane.getForeground();

        for (int i = 0; i < lineCount; i++) {
            try {
                Rectangle r = TextPaneHelper.getModelToViewRect(textPane, root.getElement(i).getStartOffset());
                if (r.y + fontHeight < clip.y) continue;
                if (r.y > clip.y + clip.height) break;

                if (i == currentLine) {
                    g2d.setColor(currentColor);
                } else {
                    g2d.setColor(normalColor);
                }

                String lineNumber = String.valueOf(i + 1);
                int stringWidth = metrics.stringWidth(lineNumber);
                g2d.drawString(lineNumber, componentWidth - borderGap - stringWidth, r.y + fontAscent);
            } catch (Exception e) {}
        }
    }
}
