package dev.kostromdan.mods.crash_assistant.common.events;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;

public class CrashAssistantEvents {
    public static void onGameJoin() {
        ProcessSignalIO.post("joined_world");
        if (!CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname) || CrashAssistantConfig.getBoolean("greeting.shown_greeting")) {
            return;
        }
        CrashAssistantConfig.set("greeting.shown_greeting", true);
        LanguageProvider.updateLang();
        TextComponentString msg = new TextComponentString(LanguageProvider.get("text.greeting1"));

        TextComponentString crashAssistantComponent = new TextComponentString("Crash Assistant");
        Style style = new Style();
        style.setColor(TextFormatting.LIGHT_PURPLE);
        style.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/KostromDan/Crash-Assistant"));
        style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(LanguageProvider.get("text.opens_url"))));
        crashAssistantComponent.setStyle(style);

        msg.appendSibling(crashAssistantComponent);
        msg.appendSibling(new TextComponentString(LanguageProvider.get("text.greeting2")));
        msg.appendSibling(CrashAssistantCommands.getModConfigComponent());
        msg.appendSibling(new TextComponentString(LanguageProvider.get("text.greeting3")));
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
