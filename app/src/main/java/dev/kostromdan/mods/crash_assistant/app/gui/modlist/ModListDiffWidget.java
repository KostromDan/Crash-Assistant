package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Compact, reusable summary row for one mod-list comparison source. */
public final class ModListDiffWidget {
    private final JPanel component;
    private final JLabel summaryLabel;
    private final JButton historyButton;
    private final JButton diffButton;
    private ModListComparison comparison;

    public ModListDiffWidget(boolean showHistoryButton, Runnable openHistory, Runnable openDiff) {
        component = new JPanel(new BorderLayout(10, 0));

        summaryLabel = new JLabel(LanguageProvider.get("gui.modlist_loading")) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.width += 4;
                return size;
            }
        };
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        component.add(summaryLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        if (showHistoryButton) {
            historyButton = new JButton(LanguageProvider.get("gui.show_modlist_history_button"));
            historyButton.addActionListener(event -> openHistory.run());
            buttons.add(historyButton);
            buttons.add(Box.createHorizontalStrut(6));
        } else {
            historyButton = null;
        }

        diffButton = new JButton(LanguageProvider.get("gui.show_modlist_diff_button"));
        diffButton.setEnabled(false);
        diffButton.setToolTipText(LanguageProvider.get("gui.modlist_loading"));
        diffButton.addActionListener(event -> openDiff.run());
        buttons.add(diffButton);
        component.add(buttons, BorderLayout.EAST);
    }

    public JPanel getComponent() {
        return component;
    }

    public ModListComparison getComparison() {
        return comparison;
    }

    public void setLoading(String title) {
        comparison = null;
        setTitle(title);
        summaryLabel.setText(LanguageProvider.get("gui.modlist_loading"));
        diffButton.setEnabled(false);
        diffButton.setToolTipText(LanguageProvider.get("gui.modlist_loading"));
    }

    public void setUnavailable(String title) {
        comparison = null;
        setTitle(title);
        String unavailable = LanguageProvider.get("gui.modlist_history.no_comparison_reference");
        summaryLabel.setText(unavailable);
        diffButton.setEnabled(false);
        diffButton.setToolTipText(unavailable);
    }

    public void setComparison(String title, ModListComparison comparison) {
        setComparison(title, comparison, comparison.createDiff());
    }

    public void setComparison(String title, ModListComparison comparison, ModListDiff diff) {
        this.comparison = comparison;
        setTitle(title);

        if (diff.isEmpty()) {
            String unchanged = LanguageProvider.get(comparison.isModpackBaselineComparison()
                    ? "gui.modlist_not_changed_label"
                    : "gui.modlist_history.unchanged");
            summaryLabel.setText(unchanged + ":");
            diffButton.setEnabled(false);
            diffButton.setToolTipText(unchanged);
            return;
        }

        String summary = "<html><div style='white-space:nowrap;'>"
                + LanguageProvider.get("gui.modlist_changed_label")
                .replace("$ADDED_MODS_COUNT$", "<span style='color:green;'>" + diff.getAddedMods().size() + "</span>")
                .replace("$REMOVED_MODS_COUNT$", "<span style='color:red;'>" + diff.getRemovedMods().size() + "</span>")
                .replace("$UPDATED_MODS_COUNT$", "<span style='color:blue;'>" + diff.getUpdatedMods().size() + "</span>")
                + "</div></html>";
        summaryLabel.setText(summary);
        diffButton.setEnabled(true);
        diffButton.setToolTipText(null);
    }

    private void setTitle(String title) {
        component.setBorder(BorderFactory.createTitledBorder(title == null ? "" : title));
    }
}
