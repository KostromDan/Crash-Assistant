package dev.kostromdan.mods.crash_assistant.forge;

import dev.kostromdan.mods.crash_assistant.CrashAssistant;
import net.minecraftforge.fml.common.Mod;

@Mod(CrashAssistant.MOD_ID)
public final class CrashAssistantForge {
    public CrashAssistantForge() {
        // Run our common setup.
        CrashAssistant.init();
    }
}
