package dev.kostromdan.mods.crash_assistant.events;

import dev.kostromdan.mods.crash_assistant.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class CrashAssistantEvents {
    public static void onGameJoin() {
        if (!CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname) || CrashAssistantConfig.getBoolean("greeting.shown_greeting")) {
            return;
        }
        CrashAssistantConfig.set("greeting.shown_greeting", true);
        LanguageProvider.updateLang();
        MutableComponent msg = Component.literal(LanguageProvider.get("text.greeting1"));
        msg.append(Component.literal("Crash Assistant")
                .withStyle(style -> style
                        .withColor(ChatFormatting.LIGHT_PURPLE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/KostromDan/Crash-Assistant"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(LanguageProvider.get("text.opens_url"))))
                ));
        msg.append(Component.literal(LanguageProvider.get("text.greeting2")));
        msg.append(CrashAssistantCommands.getModConfigComponent());
        msg.append(Component.literal(LanguageProvider.get("text.greeting3")));
        CrashAssistantCommands.sendClientMsg(msg);
    }

}
