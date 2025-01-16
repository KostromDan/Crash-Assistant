package dev.kostromdan.mods.crash_assistant.fabric.client;

import dev.kostromdan.mods.crash_assistant.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.events.CrashAssistantEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class CrashAssistantFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(CrashAssistantCommands.getCommands());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CrashAssistantEvents.onGameJoin();
        });
    }
}