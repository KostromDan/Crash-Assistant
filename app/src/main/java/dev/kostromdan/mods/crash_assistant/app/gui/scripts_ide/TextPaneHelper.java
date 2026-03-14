package dev.kostromdan.mods.crash_assistant.app.gui.scripts_ide;

import javax.swing.*;
import java.awt.*;

/**
 * Java 8 base implementation. Uses deprecated {@code modelToView(int)}.
 * Overridden in the Java 9+ multi-release variant via {@code modelToView2D(int)}.
 */
@SuppressWarnings("deprecation")
class TextPaneHelper {
    static Rectangle getModelToViewRect(JTextPane textPane, int offset) throws Exception {
        return textPane.modelToView(offset);
    }
}
