package dev.kostromdan.mods.crash_assistant.common.utils;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ClassExistenceChecker;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;


public interface CurrentGPUDetector {
    static void writeCurrentGPU() {
        try {
            long currentContext = 0L;
            if (ClassExistenceChecker.classExists("org.lwjgl.glfw.GLFW")) {
                try {
                    currentContext = GLFW.glfwGetCurrentContext();
                } catch (NoSuchMethodError ignored) {
                    // Control Flex bundles a GLFW shim without glfwGetCurrentContext.
                    if (ClassExistenceChecker.classExists("org.lwjgl.sdl.SDLVideo")) {
                        currentContext = getSDLCurrentContext();
                    }
                }
            } else if (ClassExistenceChecker.classExists("org.lwjgl.sdl.SDLVideo")) {
                currentContext = getSDLCurrentContext();
            }
            if (currentContext == 0L) {
                return;
            }

            String renderer = GL11C.glGetString(GL11C.GL_RENDERER);
            if (renderer == null) {
                return;
            }

            ProcessSignalIO.post("renderer", renderer);
        } catch (Throwable ignored) {
        }
    }

    private static long getSDLCurrentContext() throws ReflectiveOperationException {
        return (long) Class.forName("org.lwjgl.sdl.SDLVideo")
                .getMethod("SDL_GL_GetCurrentContext").invoke(null);
    }
}
