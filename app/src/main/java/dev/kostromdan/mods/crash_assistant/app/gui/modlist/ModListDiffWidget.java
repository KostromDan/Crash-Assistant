package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/** State and aligned controls for one row in the combined mod-list overview. */
public final class ModListDiffWidget {
    private final JLabel sourceLabel;
    private final JLabel summaryLabel;
    private final JLabel addedLabel;
    private final JLabel addedCountLabel;
    private final JLabel firstSeparatorLabel;
    private final JLabel removedLabel;
    private final JLabel removedCountLabel;
    private final JLabel secondSeparatorLabel;
    private final JLabel updatedLabel;
    private final JLabel updatedCountLabel;
    private final Component[] countSummaryComponents;
    private final JButton diffButton;
    private ModListComparison comparison;

    public ModListDiffWidget(Runnable openDiff) {
        sourceLabel = new JLabel();
        sourceLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

        summaryLabel = new JLabel(LanguageProvider.get("gui.modlist_loading"));
        summaryLabel.setHorizontalAlignment(JLabel.RIGHT);

        addedLabel = new JLabel(LanguageProvider.get("gui.modlist_changed_metric.added"));
        addedCountLabel = createCountLabel(new Color(0, 128, 0));
        firstSeparatorLabel = new JLabel("·");
        removedLabel = new JLabel(LanguageProvider.get("gui.modlist_changed_metric.removed"));
        removedCountLabel = createCountLabel(Color.RED);
        secondSeparatorLabel = new JLabel("·");
        updatedLabel = new JLabel(LanguageProvider.get("gui.modlist_changed_metric.updated"));
        updatedCountLabel = createCountLabel(Color.BLUE);
        countSummaryComponents = new Component[] {
                addedLabel,
                addedCountLabel,
                firstSeparatorLabel,
                removedLabel,
                removedCountLabel,
                secondSeparatorLabel,
                updatedLabel,
                updatedCountLabel
        };
        setCountSummaryVisible(false);

        diffButton = new JButton(LanguageProvider.get("gui.show_modlist_diff_button"));
        diffButton.setEnabled(false);
        diffButton.setToolTipText(LanguageProvider.get("gui.modlist_loading"));
        diffButton.addActionListener(event -> openDiff.run());
    }

    /** Adds this row to the shared GridBagLayout so every row uses the same columns. */
    public void addTo(JPanel container, int row) {
        int topInset = row == 0 ? 1 : 3;

        GridBagConstraints sourceConstraints = new GridBagConstraints();
        sourceConstraints.gridx = 0;
        sourceConstraints.gridy = row;
        sourceConstraints.weightx = 1.0;
        sourceConstraints.fill = GridBagConstraints.HORIZONTAL;
        sourceConstraints.anchor = GridBagConstraints.LINE_START;
        sourceConstraints.insets = new Insets(topInset, 0, 1, 12);
        container.add(sourceLabel, sourceConstraints);

        GridBagConstraints summaryConstraints = new GridBagConstraints();
        summaryConstraints.gridx = 1;
        summaryConstraints.gridy = row;
        summaryConstraints.gridwidth = 8;
        summaryConstraints.anchor = GridBagConstraints.LINE_END;
        summaryConstraints.insets = new Insets(topInset, 0, 1, 8);
        container.add(summaryLabel, summaryConstraints);

        addSummaryCell(container, addedLabel, 1, row, topInset, 0, 4);
        addSummaryCell(container, addedCountLabel, 2, row, topInset, 0, 0);
        addSummaryCell(container, firstSeparatorLabel, 3, row, topInset, 6, 6);
        addSummaryCell(container, removedLabel, 4, row, topInset, 0, 4);
        addSummaryCell(container, removedCountLabel, 5, row, topInset, 0, 0);
        addSummaryCell(container, secondSeparatorLabel, 6, row, topInset, 6, 6);
        addSummaryCell(container, updatedLabel, 7, row, topInset, 0, 4);
        addSummaryCell(container, updatedCountLabel, 8, row, topInset, 0, 8);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 9;
        buttonConstraints.gridy = row;
        buttonConstraints.anchor = GridBagConstraints.LINE_END;
        buttonConstraints.insets = new Insets(topInset, 0, 1, 5);
        container.add(diffButton, buttonConstraints);
    }

    private static void addSummaryCell(JPanel container,
                                       Component component,
                                       int column,
                                       int row,
                                       int topInset,
                                       int leftInset,
                                       int rightInset) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(topInset, leftInset, 1, rightInset);
        container.add(component, constraints);
    }

    public ModListComparison getComparison() {
        return comparison;
    }

    public void setLoading(String title) {
        comparison = null;
        setSource(title);
        showStatus(LanguageProvider.get("gui.modlist_loading"));
        diffButton.setEnabled(false);
        diffButton.setToolTipText(LanguageProvider.get("gui.modlist_loading"));
    }

    public void setUnavailable(String title) {
        setUnavailable(title, LanguageProvider.get("gui.modlist_history.no_comparison_reference"));
    }

    public void setUnavailable(String title, String unavailable) {
        comparison = null;
        setSource(title);
        showStatus(unavailable);
        diffButton.setEnabled(false);
        diffButton.setToolTipText(unavailable);
    }

    public void setComparison(String title, ModListComparison comparison) {
        setComparison(title, comparison, comparison.createDiff());
    }

    public void setComparison(String title, ModListComparison comparison, ModListDiff diff) {
        this.comparison = comparison;
        setSource(title);

        if (diff.isEmpty()) {
            String unchanged = LanguageProvider.get("gui.modlist_history.unchanged");
            showStatus(unchanged);
            diffButton.setEnabled(false);
            diffButton.setToolTipText(unchanged);
            return;
        }

        summaryLabel.setVisible(false);
        addedCountLabel.setText(Integer.toString(diff.getAddedMods().size()));
        removedCountLabel.setText(Integer.toString(diff.getRemovedMods().size()));
        updatedCountLabel.setText(Integer.toString(diff.getUpdatedMods().size()));
        setCountSummaryVisible(true);
        diffButton.setEnabled(true);
        diffButton.setToolTipText(null);
    }

    private static JLabel createCountLabel(Color color) {
        JLabel label = new JLabel("0");
        label.setForeground(color);
        label.setHorizontalAlignment(JLabel.LEADING);
        return label;
    }

    private void showStatus(String status) {
        setCountSummaryVisible(false);
        summaryLabel.setText(status);
        summaryLabel.setVisible(true);
    }

    private void setCountSummaryVisible(boolean visible) {
        for (Component component : countSummaryComponents) {
            component.setVisible(visible);
        }
    }

    private void setSource(String title) {
        sourceLabel.setText(title == null ? "" : title);
    }
}
