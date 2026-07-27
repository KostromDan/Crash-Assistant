package dev.kostromdan.mods.crash_assistant.app.gui;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.IntelCorruptedProcessorChecker;
import dev.kostromdan.mods.crash_assistant.app.utils.LinksHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.SwingEDT;
import dev.kostromdan.mods.crash_assistant.app.utils.ThemeUtils;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IntelChipBugWarning {

    public static String microcodeVertionString = "UNDEFINED";
    public static long microcodeVersion = -1L;
    public static final long FIRST_NOT_AFFECTED_MICROCODE_VERSION = Long.parseLong("129", 16);

    public static void showIfAffected(boolean debug) {
            ThemeUtils.ensureThemesApplied();
            if (!CrashAssistantConfig.getBoolean("intel_corrupted.enabled")) return;
            if (!IntelCorruptedProcessorChecker.isAffectedProcessor() && !debug) return;

            parseMicrocodeVersion();

            if (Objects.equals(CrashAssistantLocalConfig.get("intel_corrupted.dont_show_again"), true)) return;

            CrashAssistantApp.LOGGER.info("Showing IntelChipBugWarning");


            ControlPanel.stopMovingToTop = true;

            SwingEDT.runAndWait(() -> {
            JDialog dialog = new JDialog((Frame) null, LanguageProvider.get("gui.intel_corrupted_title"), true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            JPanel mainPanel = new JPanel(new GridBagLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(3, 3, 3, 3);

            int colIndex = 0;

            HashMap<String, String> replacements = new HashMap<String, String>() {{
                put("$LINK.INTEL_CHIP_BUG_FAQ$", "FAQ");
            }};
            String warningText = LanguageProvider.get("gui.intel_corrupted_msg", replacements);
            String microcodeText = "";

//            microcodeVertionString = "0x125";
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


            JEditorPane textPane = CrashAssistantGUI.getEditorPane(warningText, true, 600);

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
                    LinksHelper.browse(new URI(LinksProvider.INTEL_CHIP_BUG_FAQ.getLink()));
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
            dialog.setSize(dialog.getPreferredSize().width, dialog.getPreferredSize().height);
            dialog.setLocationRelativeTo(null);
            if (debug) dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            });
        CrashAssistantApp.LOGGER.info("Shown IntelChipBugWarning");
    }

    public static void parseMicrocodeVersion() {
        try {
            if (!PlatformHelp.isWindows()) return;
            byte[] cur = Advapi32Util.registryGetBinaryValue(
                    WinReg.HKEY_LOCAL_MACHINE,
                    "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0",
                    "Update Revision"
            );

            if (cur == null || cur.length < 4) {
                throw new IllegalStateException("Update Revision missing/too short");
            }

            CrashAssistantApp.LOGGER.info("Update Revision bytes: {}", IntStream.range(0, cur.length).mapToObj(i -> String.format("%02X", cur[i] & 0xFF)).collect(Collectors.joining(" ")));

            ByteBuffer bb = ByteBuffer.wrap(cur).order(ByteOrder.LITTLE_ENDIAN);
            long d0 = (bb.getInt(0) & 0xFFFFFFFFL);                          // first  DWORD
            long d1 = (cur.length >= 8) ? (bb.getInt(4) & 0xFFFFFFFFL) : 0L; // second DWORD

            long rev = (d0 != 0) ? d0 : d1;

            if (rev == 0) {
                // Treat as unknown instead of misleading "0x0"
                microcodeVersion = -1L;
                microcodeVertionString = "UNKNOWN";
                CrashAssistantApp.LOGGER.warn("Microcode revision unreadable (both DWORDs zero).");
                return;
            }

            microcodeVersion = rev;
            microcodeVertionString = String.format("0x%X", microcodeVersion);
            CrashAssistantApp.LOGGER.info("Microcode version: " + microcodeVertionString);

        } catch (Throwable e) {
            microcodeVersion = -1L;
            microcodeVertionString = "ERROR - FAILED TO GET MICROCODE";
            CrashAssistantApp.LOGGER.error("Error getting microcode version: ", e);
        }
    }

    public static void main(String[] args) {
        IntelChipBugWarning.showIfAffected(true);
    }
}
