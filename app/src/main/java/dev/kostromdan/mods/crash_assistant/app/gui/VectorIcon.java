package dev.kostromdan.mods.crash_assistant.app.gui;

import com.formdev.flatlaf.util.MultiResolutionImageSupport;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** A dependency-free Java2D icon generated from an SVG during the build. */
final class VectorIcon {
    interface Painter {
        void paint(Graphics2D graphics);
    }

    private final double viewBoxX;
    private final double viewBoxY;
    private final double viewBoxWidth;
    private final double viewBoxHeight;
    private final Painter painter;

    VectorIcon(double viewBoxX, double viewBoxY, double viewBoxWidth, double viewBoxHeight,
               Painter painter) {
        this.viewBoxX = viewBoxX;
        this.viewBoxY = viewBoxY;
        this.viewBoxWidth = viewBoxWidth;
        this.viewBoxHeight = viewBoxHeight;
        this.painter = painter;
    }

    Image createMultiResolutionImage(int size) {
        Dimension[] dimensions = {
                new Dimension(size, size),
                new Dimension(size * 2, size * 2)
        };
        return MultiResolutionImageSupport.create(0, dimensions,
                dimension -> render(dimension.width, dimension.height));
    }

    BufferedImage render(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            double scale = Math.min(width / viewBoxWidth, height / viewBoxHeight);
            graphics.translate((width - viewBoxWidth * scale) / 2d,
                    (height - viewBoxHeight * scale) / 2d);
            graphics.scale(scale, scale);
            graphics.translate(-viewBoxX, -viewBoxY);
            painter.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
