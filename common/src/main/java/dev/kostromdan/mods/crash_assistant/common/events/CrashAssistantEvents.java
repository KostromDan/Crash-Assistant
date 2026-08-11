package dev.kostromdan.mods.crash_assistant.common.events;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;

public class CrashAssistantEvents {
    public static void onGameJoin() {
        ModListUtils.scheduleAutomaticUpdate(CrashAssistant.playerNickname);
        ProcessSignalIO.post("joined_world");
        if (!CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname) || CrashAssistantConfig.getBoolean("greeting.shown_greeting")) {
            return;
        }
        CrashAssistantConfig.set("greeting.shown_greeting", true);
        LanguageProvider.updateLang();
        MutableComponent msg = Component.literal(LanguageProvider.get("text.greeting1"));
        msg.append(Component.literal("Crash Assistant")
                .withStyle(style -> style
                        .withColor(ChatFormatting.LIGHT_PURPLE)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/KostromDan/Crash-Assistant")))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(LanguageProvider.get("text.opens_url"))))
                ));
        msg.append(Component.literal(LanguageProvider.get("text.greeting2")));
        msg.append(CrashAssistantCommands.getModConfigComponent());
        msg.append(Component.literal(LanguageProvider.get("text.greeting3")));
        CrashAssistantCommands.sendClientMsg(msg);
    }

}
