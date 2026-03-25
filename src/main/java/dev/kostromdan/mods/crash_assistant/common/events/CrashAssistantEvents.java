package dev.kostromdan.mods.crash_assistant.common.events;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class CrashAssistantEvents {

    public static void onGameJoin() {
        ProcessSignalIO.post("joined_world");
        if (!CrashAssistantConfig.getModpackCreators()
            .contains(CrashAssistant.playerNickname) || CrashAssistantConfig.getBoolean("greeting.shown_greeting")) {
            return;
        }
        CrashAssistantConfig.set("greeting.shown_greeting", true);
        LanguageProvider.updateLang();
        ChatComponentText msg = new ChatComponentText(LanguageProvider.get("text.greeting1"));

        ChatComponentText crashAssistantComponent = new ChatComponentText("Crash Assistant");
        ChatStyle style = new ChatStyle();
        style.setColor(EnumChatFormatting.LIGHT_PURPLE);
        style.setChatClickEvent(
            new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/KostromDan/Crash-Assistant"));
        style.setChatHoverEvent(
            new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(LanguageProvider.get("text.opens_url"))));
        crashAssistantComponent.setChatStyle(style);

        msg.appendSibling(crashAssistantComponent);
        msg.appendSibling(new ChatComponentText(LanguageProvider.get("text.greeting2")));
        msg.appendSibling(CrashAssistantCommands.getModConfigComponent());
        msg.appendSibling(new ChatComponentText(LanguageProvider.get("text.greeting3")));
        CrashAssistantCommands.sendClientMsg(msg);
    }

    public static void afterMinecraftInit() {
        CrashAssistant.playerNickname = Minecraft.getMinecraft()
                .getSession()
                .getUsername();
        ProcessSignalIO.postInfo("username", CrashAssistant.playerNickname);
    }

    public static void onMinecraftShutdown() {
        ProcessSignalIO.post("normal_stop");
    }

    public static void onErrorScreenInit() {
        ProcessSignalIO.post("loading_error_fml");
    }

    public static void onClientLoaded() {
        if (CrashAssistant.clientLoaded) return;
        CrashAssistant.clientLoaded = true;

        if(PlatformHelp.platform == PlatformHelp.FABRIC){
            CrashAssistant.init();
        }

        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            if (CrashAssistantConfig.getModpackCreators()
                    .isEmpty()) {
                CrashAssistantConfig.addModpackCreator(CrashAssistant.playerNickname);
            }
            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update")
                    && CrashAssistantConfig.getModpackCreators()
                    .contains(CrashAssistant.playerNickname)) {
                ModListUtils.saveCurrentModList();
            }
        }

        ProcessSignalIO.post("successful_launch");
    }
}
