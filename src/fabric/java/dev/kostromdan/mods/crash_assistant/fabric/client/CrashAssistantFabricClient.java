package dev.kostromdan.mods.crash_assistant.fabric.client;

import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.fabricmc.api.ClientModInitializer;
//import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class CrashAssistantFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
//        ClientCommandManager.DISPATCHER.register(CrashAssistantCommands.getCommands());
//
//        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
//            CrashAssistantEvents.onGameJoin();
//        });
    }
}