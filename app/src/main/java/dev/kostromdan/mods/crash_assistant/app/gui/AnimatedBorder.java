package dev.kostromdan.mods.crash_assistant.app.gui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.geom.Path2D;

public class AnimatedBorder implements Border {
    private float phase = 0; // Dash phase for animation
    private float incrementer = 1;
    private boolean animating = true; // Flag to toggle animation
    private final float[] dashArray = {20, 10}; // Dash pattern: 20 units on, 10 units off
    private final Component component; // Reference to the component for repainting
    private final Color borderColor; // New field to store the border color

    // Constructor: Initialize with the component and border color
    public AnimatedBorder(Component component, Color borderColor, boolean goingLeft) {
        this.component = component;
        this.borderColor = borderColor;

        if (goingLeft) {
            phase = 300000;
            incrementer = -1;
        }

        // Animation timer: Updates phase every 50ms
        javax.swing.Timer animationTimer = new javax.swing.Timer(50, e -> {
            phase += incrementer; // Increment phase for movement
            component.repaint(); // Redraw the border
        });
        animationTimer.start();

        // Stop animation after 30 seconds
        new javax.swing.Timer(30000, e -> {
            animationTimer.stop();
            animating = false;
            component.repaint(); // Draw static border
        }).start();
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(borderColor); // Use the specified color

        // Define the border path as a continuous loop around the component
        Path2D path = new Path2D.Float();
        path.moveTo(x, y); // Top-left
        path.lineTo(x + width, y); // Top-right
        path.lineTo(x + width, y + height); // Bottom-right
        path.lineTo(x, y + height); // Bottom-left
        path.lineTo(x, y); // Back to top-left

        if (animating) {
            // Animated dashed border
            BasicStroke stroke = new BasicStroke(
                    2, // Thickness
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,
                    10,
                    dashArray, // Dash pattern
                    phase // Current phase for animation
            );
            g2d.setStroke(stroke);
        } else {
            // Static solid border after animation
            g2d.setStroke(new BasicStroke(2));
        }

        g2d.draw(path); // Draw the border
        g2d.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(2, 2, 2, 2); // Space for the 2-pixel thick border
    }

    @Override
    public boolean isBorderOpaque() {
        return false; // Border is not a filled area
    }
}