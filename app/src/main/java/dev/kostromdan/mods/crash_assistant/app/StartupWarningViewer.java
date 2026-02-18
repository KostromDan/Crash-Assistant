package dev.kostromdan.mods.crash_assistant.app;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import dev.kostromdan.mods.crash_assistant.app.utils.ThemeUtils;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;

public class StartupWarningViewer {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: StartupWarningViewer <base64_json_warnings>");
            System.exit(1);
        }

        try {
            String encoded = args[0];
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            
            Type listType = new TypeToken<List<ScriptWarning>>(){}.getType();
            List<ScriptWarning> warnings = new Gson().fromJson(json, listType);

            if (warnings == null || warnings.isEmpty()) {
                System.exit(0);
            }

            SwingUtilities.invokeLater(() -> showWarnings(warnings));

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void showWarnings(List<ScriptWarning> warnings) {
        ThemeUtils.ensureThemesApplied();
        JFrame frame = new JFrame("Crash Assistant - Startup Warnings");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel("<html><h2>Startup Warnings</h2><p>The following issues were detected by Crash Assistant scripts:</p></html>");
        mainPanel.add(header, BorderLayout.NORTH);

        JPanel warningsList = new JPanel();
        warningsList.setLayout(new BoxLayout(warningsList, BoxLayout.Y_AXIS));
        
        for (ScriptWarning warning : warnings) {
            JPanel warningPanel = new JPanel(new BorderLayout());
            warningPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
            
            JTextArea textArea = new JTextArea(warning.getMessage());
            textArea.setWrapStyleWord(true);
            textArea.setLineWrap(true);
            textArea.setEditable(false);
            textArea.setOpaque(false);
            textArea.setFont(UIManager.getFont("Label.font"));
            
            warningPanel.add(textArea, BorderLayout.CENTER);
            warningsList.add(warningPanel);
        }

        JScrollPane scrollPane = new JScrollPane(warningsList);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close and Continue");
        closeButton.addActionListener(e -> System.exit(0));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);
    }
}
