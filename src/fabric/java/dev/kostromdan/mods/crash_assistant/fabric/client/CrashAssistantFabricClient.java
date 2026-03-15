package dev.kostromdan.mods.crash_assistant.fabric.client;

import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.command.CommandSide;
import net.legacyfabric.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.legacyfabric.fabric.api.registry.CommandRegistrationCallback;

public final class CrashAssistantFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CommandRegistrationCallback.EVENT.register(registry -> {
            registry.register(new CrashAssistantCommands(), CommandSide.INTEGRATED);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CrashAssistantEvents.onGameJoin();
        });
    }
}
