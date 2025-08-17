package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.utils.IntelCorruptedProcessorChecker;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;

import javax.swing.*;
import java.awt.*;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;

public class IntelChipBugWarning {
    public static final String GIF_URL = "https://kostromdan.github.io/Crash-Assistant/assets/intel_bug.gif?raw=true";
    public static final Path LOCAL_GIF_PATH = Paths.get("local", "crash_assistant", "intel_bug.gif");

    public static String microcodeVertionString = "UNDEFINED";
    public static long microcodeVersion = -1L;
    public static final long FIRST_NOT_AFFECTED_MICROCODE_VERSION = Long.parseLong("129", 16);

    public static void showIfAffected(boolean debug) {
        synchronized (KnownCrashReasonMessage.class) {
            if (!CrashAssistantConfig.getBoolean("intel_corrupted.enabled")) return;
            if (!IntelCorruptedProcessorChecker.isAffectedProcessor() && !debug) return;

            parseMicrocodeVersion();

            if (Objects.equals(CrashAssistantLocalConfig.get("intel_corrupted.dont_show_again"), true)) return;
            boolean showGif = CrashAssistantConfig.getBoolean("intel_corrupted.show_gif");

            CrashAssistantApp.LOGGER.info("Showing IntelChipBugWarning");


            ControlPanel.stopMovingToTop = true;

            JDialog dialog = new JDialog((Frame) null, LanguageProvider.get("gui.intel_corrupted_title"), true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            JPanel mainPanel = new JPanel(new GridBagLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(3, 3, 3, 3);

            int colIndex = 0;
            JLabel gifLabel = null;
            int gifWidth = 211;
            int gifHeight = 374;

            if (showGif) {
                JPanel gifPanel = new JPanel(new BorderLayout());
                gifPanel.setPreferredSize(new Dimension(gifWidth, gifHeight));
                gifPanel.setMinimumSize(new Dimension(gifWidth, gifHeight));
                gifPanel.setMaximumSize(new Dimension(gifWidth, gifHeight));
                gifPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

                gifLabel = new JLabel("GIF", SwingConstants.CENTER);
                gifLabel.setHorizontalAlignment(SwingConstants.CENTER);
                gifLabel.setVerticalAlignment(SwingConstants.CENTER);
                gifPanel.add(gifLabel, BorderLayout.CENTER);

                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.gridheight = 2;
                gbc.weightx = 0;
                gbc.weighty = 1.0;
                gbc.fill = GridBagConstraints.VERTICAL;
                gbc.anchor = GridBagConstraints.CENTER;
                mainPanel.add(gifPanel, gbc);

                colIndex = 1;

                final JLabel finalGifLabel = gifLabel;
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
                            finalGifLabel.setText(null);
                            finalGifLabel.setIcon(loadedIcon);
                        } catch (Exception e) {
                            CrashAssistantApp.LOGGER.error("Error loading GIF: ", e);
                            finalGifLabel.setText("Failed to load GIF");
                        }
                    }
                }.execute();
            }

            HashMap<String, String> replacements = new HashMap<String, String>() {{
                put("$LINK.INTEL_CHIP_BUG_FAQ$", "FAQ");
            }};
            String warningText = LanguageProvider.get("gui.intel_corrupted_msg", replacements);
            String microcodeText = "";

//            microcodeVertionString = "0x12B";
//            microcodeVersion = Long.parseLong(microcodeVertionString.substring(2), 16);

            if (microcodeVersion != -1L) {
                boolean isAffected = microcodeVersion < FIRST_NOT_AFFECTED_MICROCODE_VERSION;
                if (!isAffected) {
                    microcodeText = "\n\n<span style='background-color:#EEFFEE;color:#006600;'>"
                            + LanguageProvider.get("gui.intel_corrupted_microcode_good", replacements)
                            + "</span>";
                } else {
                    microcodeText = "\n\n<span style='background-color:#FFEEEE;color:#990000;'>"
                            + LanguageProvider.get("gui.intel_corrupted_microcode_bad", replacements)
                            + "</span>";
                }
                microcodeText = microcodeText.replace("$MICROCODE_VERSION_CURRENT$", "<strong style='color: " + (isAffected ? "red" : "green") + ";'>" + microcodeVertionString + "</strong>");
                microcodeText = microcodeText.replace("$MICROCODE_VERSION_FIXED$", "<strong style='color: green;'>" + "0x" + Long.toHexString(FIRST_NOT_AFFECTED_MICROCODE_VERSION) + "</strong>");
            }

            warningText = warningText.replace("$CURRENT_MICROCODE_TEXT$", microcodeText);


            JEditorPane textPane = CrashAssistantGUI.getEditorPane(warningText, true);

            JScrollPane scrollPane = new JScrollPane(textPane);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

            gbc.gridx = colIndex;
            gbc.gridy = 0;
            gbc.gridheight = 1;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(scrollPane, gbc);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
            JButton readMoreButton = new JButton(LanguageProvider.get("gui.intel_corrupted_read_more"));
            readMoreButton.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URI(LinksProvider.INTEL_CHIP_BUG_FAQ.getLink()));
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.error("Error opening URL: ", ex);
                }
            });
            JButton okButton = new JButton("OK");
            okButton.addActionListener(e -> dialog.dispose());
            buttonPanel.add(readMoreButton);
            buttonPanel.add(okButton);

            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(buttonPanel, BorderLayout.CENTER);
            JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
            dontShowAgainCheck.addActionListener(e ->
                    CrashAssistantLocalConfig.set("intel_corrupted.dont_show_again", dontShowAgainCheck.isSelected())
            );
            bottomPanel.add(dontShowAgainCheck, BorderLayout.WEST);

            gbc.gridx = colIndex;
            gbc.gridy = 1;
            gbc.gridheight = 1;
            gbc.weightx = 1.0;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(bottomPanel, gbc);

            dialog.setContentPane(mainPanel);
            dialog.pack();
            dialog.setSize(870 - (showGif ? 0 : gifWidth), Math.max(dialog.getPreferredSize().height, 439+10));
            dialog.setLocationRelativeTo(null);
            if (debug) dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
        }
        CrashAssistantApp.LOGGER.info("Shown IntelChipBugWarning");
    }

    public static void parseMicrocodeVersion() {
        String fileName = "microcode_" + System.currentTimeMillis() + ".txt";
        Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), fileName);

        try {
            try {
                // PowerShell command to retrieve an Intel processor microcode version from the Windows registry.
                // This is needed to determine if the processor has vulnerable microcode which is corrupting the CPU.
                String command = ("$ErrorActionPreference = 'Continue'; " +
                        "(('0x{0:X}' -f [BitConverter]::ToUInt32((Get-ItemProperty 'HKLM:\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0' | Select-Object -ExpandProperty 'Update Revision'),0))) " +
                        "*>&1 | Out-String -Stream | Out-File \"$FILE_NAME$\" -Encoding UTF8 -NoNewline")
                        .replace("$FILE_NAME$", tempPath.toString());

                Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command)
                        .redirectErrorStream(true)
                        .start();

                process.waitFor();

                String output = new String(Files.readAllBytes(tempPath), StandardCharsets.UTF_8);

                // Remove the UTF-8 Byte Order Mark (BOM) if it exists.
                if (output.startsWith("\uFEFF")) {
                    output = output.substring(1);
                }

                String trimmedOutput = output.trim();

                if (trimmedOutput.matches("^0x[0-9A-Fa-f]+$")) {
                    microcodeVertionString = trimmedOutput;
                    microcodeVersion = Long.parseLong(microcodeVertionString.substring(2), 16);
                    CrashAssistantApp.LOGGER.info("Microcode version: " + microcodeVertionString);
                } else {
                    throw new java.io.IOException("PowerShell script failed or returned invalid format: " + output);
                }

            } finally {
                Files.deleteIfExists(tempPath);
            }
        } catch (Exception e) {
            microcodeVertionString = "ERROR - FAILED TO GET MICROCODE";
            CrashAssistantApp.LOGGER.error("Error getting microcode version: ", e);
        }
    }

    public static void main(String[] args) {
        IntelChipBugWarning.showIfAffected(true);
    }
}
