package dev.kostromdan.mods.crash_assistant.app.gui.modlist.history;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryRecord;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryStatus;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModListHistoryTableModelTest {
    private ModListHistoryTableModelTest() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        testModel();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                testNaturalColumnWidthDrivesPreferredViewport();
                testPreferredOpeningWidthShowsEveryColumnInBothTables();
                testVerticalScrollBarsDoNotHideLastColumnAtOpeningWidth();
                testInitialDialogSizeUsesPackedWidthAndScreenBounds();
                testSplitPaneStartsAndGrowsSymmetrically();
                testSelectionColorRemainsActiveWithoutFocus();
                testImportDescriptionWrapsWithoutHorizontalOverflow();
            }
        });
        System.out.println("Mod-list history table model and layout tests passed.");
    }

    private static void testModel() {
        ModListHistoryRow current = ModListHistoryRow.current(
                300L,
                ModListHistoryStatus.TITLE_SCREEN,
                Arrays.asList(new Mod("a.jar"), new Mod("b.jar")));
        ModListHistoryRow joined = ModListHistoryRow.history(new ModListHistoryRecord(
                200L,
                ModListHistoryStatus.JOINED,
                true,
                true,
                Collections.singletonList(new Mod("a.jar"))));
        ModListHistoryRow legacy = ModListHistoryRow.history(ModListHistoryRecord.legacy(
                100L,
                Collections.singletonList(new Mod("legacy.jar")),
                "config/crash_assistant/modlist.json"));

        List<ModListHistoryRow> rows = Arrays.asList(current, legacy, joined);
        ModListHistoryTableModel model = new ModListHistoryTableModel(rows);

        assertEquals(3, model.getRowCount(), "row count");
        assertEquals(3, model.getColumnCount(), "column count");
        assertEquals(Integer.class, model.getColumnClass(ModListHistoryTableModel.MODS_COUNT_COLUMN),
                "mod count type");
        assertEquals(ModListHistoryRow.class, model.getColumnClass(ModListHistoryTableModel.DATE_COLUMN),
                "date sort type");
        assertEquals(Integer.valueOf(2), model.getValueAt(0, ModListHistoryTableModel.MODS_COUNT_COLUMN),
                "current mod count");
        assertTrue(model.getRow(0).isCurrent(), "synthetic Current row");
        assertTrue(model.getRow(1).isLegacy(), "legacy snapshot stays unclassified");
        assertEquals(null, model.getRow(1).getStatus(), "legacy snapshot status");
        assertEquals(ModListHistoryStatus.JOINED, model.getRow(2).getStatus(), "joined status");
        assertTrue(!model.getRow(0).getStableId().equals(model.getRow(2).getStableId()),
                "different rows remain independently selectable");

    }

    private static void testNaturalColumnWidthDrivesPreferredViewport() {
        JTable table = new JTable(
                new Object[][]{{
                        "Крашнулось во время присоединения к миру",
                        "2026-08-11 13:39:18",
                        Integer.valueOf(1234)}},
                new Object[]{"Статус", "Дата / время", "Число модов"});

        ModListHistoryDialog.fitColumnsToContents(table);

        assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode(), "manual narrowing keeps scrolling");
        int expectedWidth = table.getColumnModel().getTotalColumnWidth()
                + new JScrollBar(JScrollBar.VERTICAL).getPreferredSize().width;
        assertEquals(expectedWidth, table.getPreferredScrollableViewportSize().width,
                "preferred viewport reserves space for every column and a vertical scrollbar");
    }

    private static void testPreferredOpeningWidthShowsEveryColumnInBothTables() {
        JTable leftTable = historyLayoutTable();
        JTable rightTable = historyLayoutTable();
        JScrollPane leftScroll = new JScrollPane(leftTable);
        JScrollPane rightScroll = new JScrollPane(rightTable);
        JSplitPane splitPane = ModListHistoryDialog.createHistorySplitPane(leftScroll, rightScroll);
        JPanel root = new JPanel(new java.awt.BorderLayout());
        root.add(splitPane, java.awt.BorderLayout.CENTER);

        root.setSize(root.getPreferredSize());
        layoutTree(root);
        ModListHistoryDialog.centerSplitPane(splitPane);
        layoutTree(root);

        assertTrue(leftTable.getColumnModel().getTotalColumnWidth()
                        <= leftScroll.getViewport().getExtentSize().width,
                "left table columns fit at preferred opening width");
        assertTrue(rightTable.getColumnModel().getTotalColumnWidth()
                        <= rightScroll.getViewport().getExtentSize().width,
                "right table columns fit at preferred opening width");
    }

    private static JTable historyLayoutTable() {
        return historyLayoutTable(1);
    }

    private static JTable historyLayoutTable(int rowCount) {
        Object[][] rows = new Object[rowCount][3];
        for (int row = 0; row < rowCount; row++) {
            rows[row][0] = "Крашнулось во время присоединения к миру";
            rows[row][1] = "2026-08-11 13:39:18";
            rows[row][2] = Integer.valueOf(1234);
        }
        JTable table = new JTable(
                rows,
                new Object[]{"Статус", "Дата / время", "Число модов"});
        ModListHistoryDialog.fitColumnsToContents(table);
        return table;
    }

    private static void testVerticalScrollBarsDoNotHideLastColumnAtOpeningWidth() {
        JTable leftTable = historyLayoutTable(50);
        JTable rightTable = historyLayoutTable(50);
        JScrollPane leftScroll = new JScrollPane(leftTable);
        JScrollPane rightScroll = new JScrollPane(rightTable);
        JSplitPane splitPane = ModListHistoryDialog.createHistorySplitPane(leftScroll, rightScroll);
        JPanel root = new JPanel(new java.awt.BorderLayout());
        root.add(splitPane, java.awt.BorderLayout.CENTER);

        root.setSize(root.getPreferredSize().width, 180);
        layoutTree(root);
        ModListHistoryDialog.centerSplitPane(splitPane);
        layoutTree(root);

        assertTrue(leftScroll.getVerticalScrollBar().isVisible(), "left vertical scrollbar is visible");
        assertTrue(rightScroll.getVerticalScrollBar().isVisible(), "right vertical scrollbar is visible");
        assertTrue(leftTable.getColumnModel().getTotalColumnWidth()
                        <= leftScroll.getViewport().getExtentSize().width,
                "left scrollbar does not hide the last column");
        assertTrue(rightTable.getColumnModel().getTotalColumnWidth()
                        <= rightScroll.getViewport().getExtentSize().width,
                "right scrollbar does not hide the last column");
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutTree((Container) child);
            }
        }
    }

    private static void testInitialDialogSizeUsesPackedWidthAndScreenBounds() {
        Dimension natural = ModListHistoryDialog.initialDialogSize(
                new Dimension(1375, 700), new Rectangle(0, 0, 1600, 900));
        assertEquals(new Dimension(1375, 700), natural, "packed size is retained on a large screen");

        Dimension minimumDefault = ModListHistoryDialog.initialDialogSize(
                new Dimension(900, 500), new Rectangle(0, 0, 1600, 900));
        assertEquals(new Dimension(1040, 580), minimumDefault, "default width remains comfortable");

        Dimension capped = ModListHistoryDialog.initialDialogSize(
                new Dimension(1800, 700), new Rectangle(100, 50, 1280, 400));
        assertEquals(new Dimension(1280, 400), capped, "dialog stays inside the usable screen");
    }

    private static void testSplitPaneStartsAndGrowsSymmetrically() {
        JPanel left = new JPanel();
        JPanel right = new JPanel();
        left.setMinimumSize(new Dimension(0, 0));
        right.setMinimumSize(new Dimension(0, 0));
        JSplitPane pane = ModListHistoryDialog.createHistorySplitPane(left, right);

        pane.setSize(900, 300);
        pane.doLayout();
        ModListHistoryDialog.centerSplitPane(pane);
        pane.doLayout();
        int leftBefore = left.getWidth();
        int rightBefore = right.getWidth();
        assertClose(leftBefore, rightBefore, 1, "initial split is centered");

        pane.setSize(1100, 300);
        pane.doLayout();
        int leftGrowth = left.getWidth() - leftBefore;
        int rightGrowth = right.getWidth() - rightBefore;
        assertClose(leftGrowth, rightGrowth, 1, "both tables receive equal resize width");

        for (int width = 1101; width <= 1300; width++) {
            pane.setSize(width, 300);
            pane.doLayout();
        }
        int incrementalLeftGrowth = left.getWidth() - leftBefore;
        int incrementalRightGrowth = right.getWidth() - rightBefore;
        assertClose(incrementalLeftGrowth, incrementalRightGrowth, 1,
                "single-pixel resize events accumulate symmetrically");

        pane.setDividerLocation(360);
        pane.doLayout();
        int manuallyMovedLeft = left.getWidth();
        int manuallyMovedRight = right.getWidth();
        for (int width = 1301; width <= 1400; width++) {
            pane.setSize(width, 300);
            pane.doLayout();
        }
        assertClose(left.getWidth() - manuallyMovedLeft, right.getWidth() - manuallyMovedRight, 1,
                "manual divider position keeps an equal resize bias");

        pane.setDividerLocation(120);
        pane.doLayout();
        for (int width = 1399; width >= 760; width--) {
            pane.setSize(width, 300);
            pane.doLayout();
        }
        assertTrue(pane.getDividerLocation() >= 0, "off-center shrink clamps the divider to the left edge");
        assertTrue(pane.getDividerLocation() <= pane.getWidth() - pane.getDividerSize(),
                "off-center shrink clamps the divider to the right edge");
    }

    private static void testSelectionColorRemainsActiveWithoutFocus() {
        Color previousActiveBackground = UIManager.getColor("Table.selectionBackground");
        Color previousActiveForeground = UIManager.getColor("Table.selectionForeground");
        Color activeBackground = new Color(24, 105, 224);
        Color activeForeground = Color.WHITE;
        try {
            UIManager.put("Table.selectionBackground", activeBackground);
            UIManager.put("Table.selectionForeground", activeForeground);
            JTable table = new JTable(1, 1);

            ModListHistoryDialog.keepSelectionColorsActive(table);

            assertEquals(activeBackground, table.getSelectionBackground(), "active selection background");
            assertEquals(activeForeground, table.getSelectionForeground(), "active selection foreground");
            assertTrue(table.getSelectionBackground() != activeBackground,
                    "selection background is independent from L&F focus toggling");
            assertTrue(table.getSelectionForeground() != activeForeground,
                    "selection foreground is independent from L&F focus toggling");
        } finally {
            UIManager.put("Table.selectionBackground", previousActiveBackground);
            UIManager.put("Table.selectionForeground", previousActiveForeground);
        }
    }

    private static void testImportDescriptionWrapsWithoutHorizontalOverflow() {
        String text = "Compare the current launch with a modlist.txt previously generated by Crash Assistant "
                + "for you or another player. This is useful for mod and modpack developers who need to restore "
                + "exactly the set of mods installed when that player's crash report was created.";
        int fixedWidth = 420;

        JTextArea pane = ModListHistoryDialog.createFixedWidthDescriptionPane(text, fixedWidth);
        JPanel descriptionPanel = new JPanel(new java.awt.BorderLayout());
        descriptionPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 0, 12));
        descriptionPanel.add(pane, java.awt.BorderLayout.CENTER);
        descriptionPanel.setSize(fixedWidth + 24, pane.getPreferredSize().height + 12);
        layoutTree(descriptionPanel);

        assertEquals(fixedWidth, pane.getWidth(), "description receives the dialog's available width");

        Insets insets = pane.getInsets();
        int contentLeft = insets.left;
        int contentRight = pane.getWidth() - insets.right;
        Set<Integer> renderedLines = new HashSet<Integer>();
        try {
            for (int position = 0; position < pane.getDocument().getLength(); position++) {
                Rectangle caret = pane.modelToView(position);
                assertTrue(caret != null, "description character has a rendered position at " + position);
                assertTrue(caret.x >= contentLeft,
                        "description character stays inside the left edge at " + position);
                assertTrue(caret.x + Math.max(1, caret.width) <= contentRight,
                        "description character stays inside the right edge at " + position
                                + ": x=" + caret.x + ", width=" + caret.width
                                + ", right=" + contentRight);
                renderedLines.add(Integer.valueOf(caret.y));
            }
        } catch (BadLocationException e) {
            throw new AssertionError("Could not inspect rendered import-description text", e);
        }
        assertTrue(renderedLines.size() > 1, "long description actually wraps to multiple rendered lines");
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertClose(int left, int right, int tolerance, String label) {
        if (Math.abs(left - right) > tolerance) {
            throw new AssertionError(label + ": expected values within " + tolerance
                    + ", got " + left + " and " + right);
        }
    }
}
