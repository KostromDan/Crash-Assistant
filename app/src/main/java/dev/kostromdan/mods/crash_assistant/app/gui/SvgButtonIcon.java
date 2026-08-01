package dev.kostromdan.mods.crash_assistant.app.gui;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

/** Keeps a generated vector glyph rasterized at the button's actual available content size. */
final class SvgButtonIcon {
    private SvgButtonIcon() {
    }

    static void install(JButton button, VectorIcon vectorIcon) {
        Binding binding = new Binding(button, new CachedVectorIcon(vectorIcon));
        button.addComponentListener(binding);
        button.addPropertyChangeListener(binding);
        binding.refresh();
        SwingUtilities.invokeLater(binding::refresh);
    }

    private static final class CachedVectorIcon {
        private final VectorIcon vectorIcon;
        private final Map<Integer, Icon> rasterCache = new HashMap<Integer, Icon>();

        private CachedVectorIcon(VectorIcon vectorIcon) {
            this.vectorIcon = vectorIcon;
        }

        private synchronized Icon iconAt(int side) {
            Icon icon = rasterCache.get(side);
            if (icon == null) {
                icon = new ImageIcon(vectorIcon.createMultiResolutionImage(side));
                rasterCache.put(side, icon);
            }
            return icon;
        }
    }

    private static final class Binding extends ComponentAdapter implements PropertyChangeListener {
        private final JButton button;
        private final CachedVectorIcon vectorIcon;
        private int currentSide = -1;

        private Binding(JButton button, CachedVectorIcon vectorIcon) {
            this.button = button;
            this.vectorIcon = vectorIcon;
        }

        @Override
        public void componentResized(ComponentEvent event) {
            refresh();
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            String property = event.getPropertyName();
            if ("border".equals(property) || "margin".equals(property) || "UI".equals(property)) {
                SwingUtilities.invokeLater(this::refresh);
            }
        }

        private void refresh() {
            Dimension size = button.getSize();
            if (size.width <= 0 || size.height <= 0) {
                size = button.getPreferredSize();
            }
            Insets insets = button.getInsets();
            int availableWidth = size.width - insets.left - insets.right;
            int availableHeight = size.height - insets.top - insets.bottom;
            int side = Math.max(1, Math.min(availableWidth, availableHeight));
            if (side == currentSide) return;

            currentSide = side;
            button.setIcon(vectorIcon.iconAt(side));
        }
    }
}
