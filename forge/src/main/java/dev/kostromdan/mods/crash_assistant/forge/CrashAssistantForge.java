package dev.kostromdan.mods.crash_assistant.forge;

import com.mojang.brigadier.CommandDispatcher;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

@Mod(CrashAssistant.MOD_ID)
public final class CrashAssistantForge {
    public CrashAssistantForge() {
        CrashAssistant.init();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            // Try new EventBus 7 registration via reflection
            if (registerNewEventBusListeners()) {
                return;
            }
            // Fallback: EventBus 6
            MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> {
                CommandDispatcher<CommandSourceStack> disp = event.getDispatcher();
                disp.register(CrashAssistantCommands.getCommands());
            });
            MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                CrashAssistantEvents.onGameJoin();
            });
        });
    }

    /**
     * Attempt to register listeners using EventBus 7's BUS fields via reflection.
     * @return true if new bus was found and listeners registered, false otherwise
     */
    private boolean registerNewEventBusListeners() {
        try {
            // Register client commands listener
            Class<?> cmdEvt = Class.forName("net.minecraftforge.client.event.RegisterClientCommandsEvent");
            Field cmdBusField = cmdEvt.getField("BUS");
            Object cmdBus = cmdBusField.get(null);
            Method addCmd = cmdBus.getClass().getMethod("addListener", Consumer.class);
            Consumer<Object> cmdListener = evt -> {
                RegisterClientCommandsEvent e = (RegisterClientCommandsEvent) evt;
                CommandDispatcher<CommandSourceStack> disp = e.getDispatcher();
                disp.register(CrashAssistantCommands.getCommands());
            };
            addCmd.invoke(cmdBus, cmdListener);

            // Register player login listener
            Class<?> loginEvt = Class.forName("net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent");
            Field loginBusField = loginEvt.getField("BUS");
            Object loginBus = loginBusField.get(null);
            Method addLogin = loginBus.getClass().getMethod("addListener", Consumer.class);
            Consumer<Object> loginListener = evt -> CrashAssistantEvents.onGameJoin();
            addLogin.invoke(loginBus, loginListener);

            return true;
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ex) {
            // EventBus 7 classes not present
            return false;
        } catch (Exception ex) {
            // Reflection failure
            return false;
        }
    }
}
