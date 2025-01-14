package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.DragAndDrop;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import gs.mclo.api.MclogsClient;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class CrashAssistantGUI {
    public static final MclogsClient MCLogsClient = new MclogsClient("CrashAssistant");
    private final JFrame frame;
    private static FileListPanel fileListPanel;
    private static ControlPanel controlPanel;
    private static HashSet<JComponent> highlightedButtons = new HashSet<>();


    public CrashAssistantGUI(Map<String, Path> availableLogs) {
        LanguageProvider.updateLang();
        frame = new JFrame(LanguageProvider.get("gui.window_name"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        String titleText = CrashAssistantApp.crashed_with_report ?
                LanguageProvider.get("gui.title_crashed_with_report") :
                LanguageProvider.get("gui.title_crashed_without_report");
        JLabel titleLabel = new JLabel(titleText, SwingConstants.LEFT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));

        String commentText = LanguageProvider.get("gui.comment_under_title", new HashSet<>() {{
            add("$SUPPORT_NAME$");
            add("$LANG.gui.upload_all_comment$");
        }});
        if (CrashAssistantConfig.get("general.show_dont_send_screenshot_of_gui_notice")) {
            String screenshotNoticeText = LanguageProvider.get("gui.comment_under_title_screenshot_notice");
            commentText += "\n<span style='color:red;'><b>" + screenshotNoticeText + "</b></span>";
        }

        JEditorPane commentPane = getEditorPane(commentText);

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
        labelPanel.add(titleLabel);
        if (!commentText.isEmpty()) {
            labelPanel.add(commentPane);
        }

        frame.add(labelPanel, BorderLayout.NORTH);

        fileListPanel = new FileListPanel();
        frame.add(fileListPanel.getScrollPane(), BorderLayout.CENTER);

        controlPanel = new ControlPanel(fileListPanel);
        frame.add(controlPanel.getPanel(), BorderLayout.SOUTH);

        int heightWithoutScrollPane = frame.getPreferredSize().height;

        for (Map.Entry<String, Path> entry : availableLogs.entrySet()) {
            fileListPanel.addFile(entry.getKey(), entry.getValue());
        }
        DragAndDrop.enableDragAndDrop(fileListPanel.getScrollPane(), fileListPanel.fileListPanelFilesDragAndDrop);

        frame.setSize(Math.max(Math.max(fileListPanel.getFileListPanel().getPreferredSize().width + 12, controlPanel.getPanel().getPreferredSize().width) + 26, labelPanel.getPreferredSize().width + 20),
                Math.min(heightWithoutScrollPane + fileListPanel.getFileListPanel().getPreferredSize().height + 39, 700));
        frame.setMinimumSize(new Dimension(frame.getSize().width, heightWithoutScrollPane + 73));

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

                if ("LANG.gui.github_gist_link".equals(description)) {
                    try {
                        Desktop.getDesktop().browse(new URL("https://gist.github.com/").toURI());
                    } catch (Exception exception) {
                        CrashAssistantApp.LOGGER.error("Failed to open in link browser: ", exception);
                    }
                    return;
                }

                JComponent componentToHighlight;
                if ("LANG.gui.upload_all_comment".equals(description)) {
                    componentToHighlight = controlPanel.uploadAllButton;
                } else if ("LANG.gui.file_list_label".equals(description)) {
                    componentToHighlight = fileListPanel.getScrollPane();
                    if (ControlPanel.dialog != null) {
                        ControlPanel.dialog.dispose();
                    }
                } else if ("SUPPORT_NAME".equals(description)) {
                    componentToHighlight = controlPanel.requestHelpButton;
                } else {
                    CrashAssistantApp.LOGGER.error("Unsupported hyperlink event: " + description);
                    return;
                }
                CrashAssistantGUI.highlightButton(componentToHighlight, new Color(255, 100, 100), 3000);
            }
        };
    }

    public static JEditorPane getEditorPane(String text) {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setText("<html><div style='white-space:nowrap;'>" + text.replaceAll("\n", "<br>") + "</div></html>");

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
}



