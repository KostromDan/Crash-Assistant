package dev.kostromdan.mods.crash_assistant.common.utils;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;

import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.glGetString;


public interface CurrentGPUDetector {
    static void writeCurrentGPU() {
        try {
            String renderer = glGetString(GL_RENDERER);
            if (renderer == null) {
                return;
            }
            ProcessSignalIO.post("renderer", renderer);
        } catch (Exception ignored) {
        }
    }
}
