package dev.kostromdan.mods.crash_assistant.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.kostromdan.mods.crash_assistant.common.utils.CurrentGPUDetector;

public final class CrashAssistant {

    public static final String MOD_ID = "crash_assistant";
    public static final Logger LOGGER = LogManager.getLogger();
    public static boolean clientLoaded = false;
    public static String playerNickname;

    public static void init() {
        CurrentGPUDetector.writeCurrentGPU();
    }
}
