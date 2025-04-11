package dev.kostromdan.mods.crash_assistant.fabric;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import net.fabricmc.api.ModInitializer;

public final class CrashAssistantFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CrashAssistant.init();
    }
}
