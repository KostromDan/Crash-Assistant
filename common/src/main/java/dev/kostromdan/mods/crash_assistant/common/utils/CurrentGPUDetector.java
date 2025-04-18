package dev.kostromdan.mods.crash_assistant.common.utils;

import org.lwjgl.opengl.GL11C;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface CurrentGPUDetector {
    static void writeCurrentGPU() {
        try {
            String renderer = GL11C.glGetString(GL11C.GL_RENDERER);
            if (renderer == null) {
                return;
            }
            String rendererFileName = "renderer" + ProcessHandle.current().pid() + ".tmp";
            Path rendererFilePath = Paths.get("local", "crash_assistant", rendererFileName);
            Files.write(rendererFilePath, renderer.getBytes());
        } catch (Exception ignored) {
        }
    }
}
