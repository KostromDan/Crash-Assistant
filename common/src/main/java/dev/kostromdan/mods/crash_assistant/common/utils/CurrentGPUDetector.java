package dev.kostromdan.mods.crash_assistant.common.utils;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;


public interface CurrentGPUDetector {
    static void writeCurrentGPU() {
        try {
            long currentContext = GLFW.glfwGetCurrentContext();
            if (currentContext == 0L) {
                return;
            }

            String renderer = GL11C.glGetString(GL11C.GL_RENDERER);
            if (renderer == null) {
                return;
            }

            ProcessSignalIO.post("renderer", renderer);
        } catch (Exception ignored) {
        }
    }
}
