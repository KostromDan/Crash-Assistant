package dev.kostromdan.mods.crash_assistant.common.events;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;

public class CrashAssistantEvents {
    public static void onGameJoin() {
        ModListUtils.scheduleAutomaticUpdate(CrashAssistant.playerNickname);
        ProcessSignalIO.post("joined_world");
        if (!CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname) || CrashAssistantConfig.getBoolean("greeting.shown_greeting")) {
            return;
        }
        CrashAssistantConfig.set("greeting.shown_greeting", true);
        LanguageProvider.updateLang();
        TextComponent msg = new TextComponent(LanguageProvider.get("text.greeting1"));

        // Create and style the "Crash Assistant" component
        TextComponent crashAssistantComponent = new TextComponent("Crash Assistant");
        Style style = new Style()
                .setColor(ChatFormatting.LIGHT_PURPLE)
                .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/KostromDan/Crash-Assistant"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(LanguageProvider.get("text.opens_url"))));
        crashAssistantComponent.setStyle(style);

        msg.append(crashAssistantComponent);
        msg.append(new TextComponent(LanguageProvider.get("text.greeting2")));
        msg.append(CrashAssistantCommands.getModConfigComponent());
        msg.append(new TextComponent(LanguageProvider.get("text.greeting3")));
        CrashAssistantCommands.sendClientMsg(msg);
    }
}
