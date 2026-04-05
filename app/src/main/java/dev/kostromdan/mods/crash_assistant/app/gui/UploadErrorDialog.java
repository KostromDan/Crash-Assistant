package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.exceptions.UploadException;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.awt.*;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class UploadErrorDialog {
    public static final String gifName = "darg_drop_guide.gif";
    public static final String GIF_URL = "https://kostromdan.github.io/Crash-Assistant/assets/" + gifName + "?raw=true";
    public static final Path LOCAL_GIF_PATH = Paths.get("local", "crash_assistant", gifName);

    private UploadErrorDialog() {
    }

    public static boolean isNetworkUploadError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UploadException && ((UploadException) current).isNetworkError()) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("http error: ")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static void show(Component parent, Log log, String errorMessage) {
        ControlPanel.stopMovingToTop = true;
        JDialog dialog = new JDialog((Frame) null, LanguageProvider.get("gui.failed_to_upload_file") + "!", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        String message = LanguageProvider.get("gui.upload_network_error_message")
                .replace("$LOG_NAME$", "<strong>" + escapeHtml(log.getName()) + "</strong>")
                .replace("$ERROR_MESSAGE$", "<strong>" + escapeHtml(errorMessage) + "</strong>");

        JEditorPane textPane = CrashAssistantGUI.getEditorPane(message, true, 600);

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(scrollPane, gbc);

        int gifWidth = 1328;
        int gifHeight = 506;

        JPanel gifPanel = new JPanel(new BorderLayout());
        gifPanel.setPreferredSize(new Dimension(gifWidth, gifHeight));
        gifPanel.setMinimumSize(new Dimension(gifWidth, gifHeight));
        gifPanel.setMaximumSize(new Dimension(gifWidth, gifHeight));
        gifPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        JLabel gifLabel = new JLabel("GIF", SwingConstants.CENTER);
        gifLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gifLabel.setVerticalAlignment(SwingConstants.CENTER);
        gifPanel.add(gifLabel, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(gifPanel, gbc);

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                LOCAL_GIF_PATH.getParent().toFile().mkdirs();

                if (!LOCAL_GIF_PATH.toFile().isFile()) {
                    try (InputStream in = new URL(GIF_URL).openStream();
                         FileOutputStream out = new FileOutputStream(LOCAL_GIF_PATH.toFile())) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }

                Image image = Toolkit.getDefaultToolkit().createImage(LOCAL_GIF_PATH.toFile().getAbsolutePath());
                MediaTracker tracker = new MediaTracker(new JPanel());
                tracker.addImage(image, 0);
                tracker.waitForAll();
                return new ImageIcon(image);
            }

            @Override
            protected void done() {
                try {
                    ImageIcon loadedIcon = get();
                    gifLabel.setText(null);
                    gifLabel.setIcon(loadedIcon);
                } catch (Exception e) {
                    CrashAssistantApp.LOGGER.error("Error loading GIF: ", e);
                    gifLabel.setText(LanguageProvider.get("gui.upload_network_error_gif_failed"));
                }
            }
        }.execute();

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(buttonPanel, gbc);

        dialog.setContentPane(mainPanel);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getPreferredSize().width, 650), dialog.getPreferredSize().height);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String formatErrorMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) return "Unknown network error";
        message = message.replace("An error occurred when uploading file: ", "")
                .replace("An error occurred when uploading modlist diff: ", "")
                .replace("Error while uploading log to mclo.gs:\n", "")
                .trim();

        StringBuilder cleaned = new StringBuilder();
        for (String line : message.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") || trimmed.startsWith("... ")) {
                break;
            }
            if (cleaned.length() > 0) cleaned.append('\n');
            cleaned.append(line);
        }
        message = cleaned.toString().trim();

        return message.isEmpty() ? "Unknown network error" : message;
    }

    public static void main(String[] args) {
        show(null, new Log(LogType.LOG, Paths.get("logs/latest.log")), "HTTP error: 500");
    }
}
