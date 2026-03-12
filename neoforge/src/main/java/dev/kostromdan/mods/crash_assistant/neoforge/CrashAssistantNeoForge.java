package dev.kostromdan.mods.crash_assistant.neoforge;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(CrashAssistant.MOD_ID)
public class CrashAssistantNeoForge {
    public CrashAssistantNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        CrashAssistant.init();
    }
}
