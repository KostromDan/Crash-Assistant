package dev.kostromdan.mods.crash_assistant.app.gui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

public class AnimatedBorder implements Border {
    private float phase = 0; // Dash phase for animation
    private float incrementer = 1;
    private boolean animating = true; // Flag to toggle animation
    private final float[] dashArray = {20, 10}; // Standard dash pattern
    private final Component component; // Reference to the component for repainting
    private final Color borderColor; // Store the border color

    public AnimatedBorder(Component component, Color borderColor, boolean goingLeft) {
        this.component = component;
        this.borderColor = borderColor;

        if (goingLeft) {
            phase = 300000;
            incrementer = -1;
        }

        // Animation timer: Updates phase every 50ms
        javax.swing.Timer animationTimer = new javax.swing.Timer(50, e -> {
            if (component.isShowing()) {
                phase += incrementer; // Increment phase for movement
                component.repaint(); // Redraw the border
            }
        });
        animationTimer.start();

        // Stop animation after 30 seconds to save resources
        new javax.swing.Timer(30000, e -> {
            animationTimer.stop();
            animating = false;
            component.repaint(); // Draw static border
        }).start();
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g.create();

        // Get the current screen scale factor (e.g., 1.0, 1.25, 1.5) from the graphics transform.
        AffineTransform at = g2d.getTransform();
        double scaleX = at.getScaleX();
        if (scaleX == 0) scaleX = 1.0;

        // Calculate the stroke width that corresponds to exactly 1 physical pixel on the screen.
        // This prevents blurring and ensures a crisp hairline border on all DPI scales.
        float onePhysicalPixel = 1.0f / (float) scaleX;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // NORMALIZE forces coordinates to snap to pixel centers, essential for 1px lines.
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        g2d.setColor(borderColor);

        // Calculate offset to center the stroke on the pixel grid.
        double offset = onePhysicalPixel / 2.0;

        // Subtract a microscopic safety gap to prevent clipping artifacts.
        // On 150% scaling, the mathematical edge often hits the clip boundary, causing the line to disappear.
        // This gap keeps the geometry strictly inside the visible area.
        double safetyGap = 0.1;

        Rectangle2D rect = new Rectangle2D.Double(
                x + offset,
                y + offset,
                width - onePhysicalPixel - safetyGap,
                height - onePhysicalPixel - safetyGap
        );

        if (animating) {
            BasicStroke stroke = new BasicStroke(
                    onePhysicalPixel,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,
                    10.0f,
                    dashArray,
                    phase
            );
            g2d.setStroke(stroke);
        } else {
            g2d.setStroke(new BasicStroke(onePhysicalPixel, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        }

        g2d.draw(rect);
        g2d.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        // Return 1px inset as the border is visually 1 physical pixel wide
        return new Insets(1, 1, 1, 1);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}