package dev.kostromdan.mods.crash_assistant.app.gui.scripts_ide;

import javax.swing.*;
import java.awt.*;

/**
 * Java 9+ implementation. Uses {@code modelToView2D(int)} which returns {@code Rectangle2D}.
 */
class TextPaneHelper {
    static Rectangle getModelToViewRect(JTextPane textPane, int offset) throws Exception {
        return textPane.modelToView2D(offset).getBounds();
    }
}
