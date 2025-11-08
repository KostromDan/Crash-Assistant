package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FileListPanel {
    public final Set<FilePanel> filePanelList = Collections.synchronizedSet(new LinkedHashSet<>());
    public final List<File> fileListPanelFilesDragAndDrop = new ArrayList<>();
    public static JDialog currentLogSelectionDialog = null;
    private final JPanel fileListPanel;
    private final JScrollPane scrollPane;

    public FileListPanel() {
        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(fileListPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createTitledBorder(LanguageProvider.get("gui.file_list_label")));
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public JPanel getFileListPanel() {
        return fileListPanel;
    }

    public Set<FilePanel> getFilePanelList(){
        synchronized (filePanelList) {
            return new LinkedHashSet<>(filePanelList);
        }
    }

    public void addLog(Log log) {
        FilePanel filePanel = new FilePanel(log);
        filePanelList.add(filePanel);

        fileListPanel.add(filePanel.getPanel());
        fileListPanelFilesDragAndDrop.add(log.getFile());
        fileListPanel.revalidate();
    }
}

