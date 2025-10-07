package dev.kostromdan.mods.crash_assistant.common.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.utils.ManualCrashThrower;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiffStringBuilder;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.utils.HeapDumper;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ThreadDumper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.event.ClickEvent.Action;

import static io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess.UNSAFE;

public class CrashAssistantCommands {
    public static final HashMap<String, String> supportedCrashCommands = new HashMap<String, String>() {
        {
            this.put("game", "Minecraft");
            this.put("jvm", "JVM");
            this.put("no_crash", "noCrash");
        }
    };
    public static final HashSet<String> supportedCrashArgs = new HashSet<String>() {
        {
            this.add("--withThreadDump");
            this.add("--withHeapDump");
            this.add("--GCBeforeHeapDump");
        }
    };
    public static Instant lastCrashCommand = Instant.ofEpochMilli(0L);
    public static boolean isDeadLocked = false;
    private static String latestDiffText = "";
    private static String latestNickname = "";

    public static <S> LiteralArgumentBuilder<S> getCommands() {
        return (LiteralArgumentBuilder<S>)((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("crash_assistant")
                .then(
                        ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("modlist")
                                .then(LiteralArgumentBuilder.literal("save").executes(CrashAssistantCommands::saveModlist)))
                                .then(LiteralArgumentBuilder.literal("diff").executes(CrashAssistantCommands::showDiff))
                ))
                .then(
                        ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("crash").requires(c -> CrashAssistantConfig.getBoolean("crash_command.enabled")))
                                .then(
                                        ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("to_crash", StringArgumentType.string())
                                                .suggests(new CrashAssistantCommands.CrashCommandsSuggestionProvider())
                                                .executes(CrashAssistantCommands::crash))
                                                .then(getCrashArg(1).then(getCrashArg(2).then(getCrashArg(3))))
                                )
                );
    }

    public static ITextComponent getModConfigComponent() {
        TextComponentString component = new TextComponentString("[mod config]");
        Style style = new Style()
                .setColor(TextFormatting.YELLOW)
                .setClickEvent(new ClickEvent(Action.OPEN_FILE, CrashAssistantConfig.getConfigPath().toAbsolutePath().toString()))
                .setHoverEvent(
                        new HoverEvent(
                                net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT, new TextComponentString(LanguageProvider.get("commands.mod_config_tooltip"))
                        )
                );
        component.setStyle(style);
        return component;
    }

    public static ITextComponent getCopyNicknameComponent(String playerNickname) {
        latestNickname = playerNickname;
        TextComponentString component = new TextComponentString("[nickname]");
        Style style = new Style()
                .setColor(TextFormatting.YELLOW)
                .setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/crash_assistant crash copy_to_clipboard nickname"))
                .setHoverEvent(
                        new HoverEvent(
                                net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT, new TextComponentString(LanguageProvider.get("commands.nickname_tooltip"))
                        )
                );
        component.setStyle(style);
        return component;
    }

    public static ITextComponent getCopyDiffComponent(ModListDiffStringBuilder diff) {
        latestDiffText = diff.toText();
        TextComponentString component = new TextComponentString("[" + LanguageProvider.get("commands.diff_copy") + "]");
        Style style = new Style()
                .setColor(TextFormatting.YELLOW)
                .setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/crash_assistant crash copy_to_clipboard"))
                .setHoverEvent(
                        new HoverEvent(net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT, new TextComponentString(LanguageProvider.get("commands.diff_tooltip")))
                );
        component.setStyle(style);
        return component;
    }

    public static void sendClientMsg(ITextComponent message) {
        Minecraft mc = Minecraft.getInstance();
        mc.addScheduledTask(() -> mc.ingameGUI.getChatGUI().printChatMessage(message));
    }

    public static boolean checkModlistFeatureEnabled() {
        LanguageProvider.updateLang();
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            return true;
        } else {
            TextComponentString msg = new TextComponentString("");
            msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.modlist_disabled_error_msg")));
            msg.appendSibling(getModConfigComponent());
            msg.setStyle(new Style().setColor(TextFormatting.RED));
            sendClientMsg(msg);
            return false;
        }
    }

    public static int saveModlist(CommandContext<?> context) {
        if (!checkModlistFeatureEnabled()) {
            return 0;
        } else {
            TextComponentString msg = new TextComponentString("");
            if (CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname)) {
                ModListUtils.saveCurrentModList();
                msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.modlist_overwritten_success")));
                if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update")) {
                    TextComponentString autoUpdateMsg = new TextComponentString(LanguageProvider.get("commands.modlist_auto_update_msg"));
                    autoUpdateMsg.setStyle(new Style().setColor(TextFormatting.WHITE));
                    msg.appendSibling(autoUpdateMsg);
                } else {
                    TextComponentString enableAutoUpdateMsg = new TextComponentString(LanguageProvider.get("commands.modlist_enable_auto_update_msg"));
                    enableAutoUpdateMsg.setStyle(new Style().setColor(TextFormatting.WHITE));
                    msg.appendSibling(enableAutoUpdateMsg);
                }

                msg.appendSibling(getModConfigComponent());
                msg.setStyle(new Style().setColor(TextFormatting.GREEN));
            } else {
                msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.not_creator_error_msg")));
                msg.appendSibling(getCopyNicknameComponent(CrashAssistant.playerNickname));
                msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.add_to_creator_list_msg")));
                msg.appendSibling(getModConfigComponent());
                msg.setStyle(new Style().setColor(TextFormatting.RED));
            }

            sendClientMsg(msg);
            return 0;
        }
    }

    public static int showDiff(CommandContext<?> context) {
        if (!checkModlistFeatureEnabled()) {
            return 0;
        } else {
            ModListDiff diff = ModListDiff.getDiff(false);
            TextComponentString msg = new CrashAssistantCommands.ComponentModListDiffStringBuilder(diff.generateDiffMsg(false)).toComponent();
            msg.appendSibling(getCopyDiffComponent(diff.generateDiffMsg(true)));
            sendClientMsg(msg);
            return 0;
        }
    }

    private static int deadlockIntegratedServer(CommandContext<?> context) {
        // Not implemented for 1.13.2; avoid direct server executor API differences
        isDeadLocked = true;
        return 0;
    }

    private static int releaseIntegratedServer(CommandContext<?> context) {
        isDeadLocked = false;
        return 0;
    }

    public static int crash(CommandContext<?> context) {
        LanguageProvider.updateLang();
        TextComponentString msg = new TextComponentString("");
        String toCrash = "null";

        try {
            toCrash = (String)context.getArgument("to_crash", String.class);
        } catch (IllegalArgumentException var7) {
            TextComponentString errorMsg = new TextComponentString(
                    LanguageProvider.get("commands.crash_command_validation_failed_to_crash") + " '" + toCrash + "'"
            );
            errorMsg.setStyle(new Style().setColor(TextFormatting.RED));
            sendClientMsg(errorMsg);
            return 0;
        }

        if (!"copy_to_clipboard".equals(toCrash)) {
            if (!supportedCrashCommands.containsKey(toCrash)) {
                TextComponentString errorMsg = new TextComponentString(
                        LanguageProvider.get("commands.crash_command_validation_failed_to_crash") + " '" + toCrash + "'"
                );
                errorMsg.setStyle(new Style().setColor(TextFormatting.RED));
                sendClientMsg(errorMsg);
                return 0;
            } else {
                toCrash = supportedCrashCommands.get(toCrash);
                int secondsToCrash = CrashAssistantConfig.<Integer>get("crash_command.seconds");
                boolean noCrash = Objects.equals(toCrash, "noCrash");
                if (secondsToCrash > 0 && !Instant.now().isBefore(lastCrashCommand.plusSeconds((long)secondsToCrash)) && !noCrash) {
                    lastCrashCommand = Instant.now();
                    msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.crash_command_1")));
                    TextComponentString toCrashComponent = new TextComponentString(toCrash);
                    toCrashComponent.setStyle(new Style().setColor(TextFormatting.YELLOW));
                    msg.appendSibling(toCrashComponent);
                    msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.crash_command_2")));
                    TextComponentString secondsComponent = new TextComponentString(Integer.toString(secondsToCrash));
                    secondsComponent.setStyle(new Style().setColor(TextFormatting.YELLOW));
                    msg.appendSibling(secondsComponent);
                    msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.crash_command_3")));
                    msg.setStyle(new Style().setColor(TextFormatting.RED));
                    sendClientMsg(msg);
                    return 0;
                } else {
                    final String toCrashFinal = toCrash;
                    final boolean noCrashFinal = noCrash;
                    List<String> args = parseCrashArgs(context);
                    new Thread(() -> {
                        if (validateCrashArgs(args)) {
                            if (!args.isEmpty()) {
                                TextComponentString applyingArgsMsg = new TextComponentString(LanguageProvider.get("commands.crash_command_applying_args"));
                                applyingArgsMsg.setStyle(new Style().setColor(TextFormatting.YELLOW));
                                sendClientMsg(applyingArgsMsg);

                                try {
                                    Thread.sleep(100L);
                                } catch (InterruptedException var6x) {
                                    throw new RuntimeException(var6x);
                                }
                            }

                            if (args.contains("--withThreadDump")) {
                                CrashAssistant.LOGGER.error("Detected '--withThreadDump' crash command argument. ThreadDump:\n" + ThreadDumper.obtainThreadDump());
                            }

                            if (args.contains("--withHeapDump")) {
                                if (args.contains("--GCBeforeHeapDump")) {
                                    CrashAssistant.LOGGER.error("Detected '--GCBeforeHeapDump' crash command argument. Performing garbage collection before heap dump.");
                                    System.gc();
                                }

                                CrashAssistant.LOGGER.error("Detected '--withHeapDump' crash command argument. Creating heap dump.");

                                try {
                                    CrashAssistant.LOGGER.error("Created heap dump at: " + HeapDumper.createHeapDump());
                                } catch (Exception var5x) {
                                    CrashAssistant.LOGGER.error("Failed to create heap dump.", var5x);
                                }
                            }

                            if (!noCrashFinal) {
                                TextComponentString crashingMsg = new TextComponentString(LanguageProvider.get("commands.crash_command_crashing"));
                                crashingMsg.setStyle(new Style().setColor(TextFormatting.RED));
                                sendClientMsg(crashingMsg);
                            } else {
                                sendClientMsg(new TextComponentString(LanguageProvider.get("commands.crash_command_done")));
                            }

                            if (Objects.equals(toCrashFinal, "Minecraft")) {
                                Minecraft.getInstance().addScheduledTask(() -> ManualCrashThrower.crashGame("Minecraft crashed by '/crash_assistant crash' command."));
                            } else if (Objects.equals(toCrashFinal, "JVM")) {
                                CrashAssistant.LOGGER.error("JVM crashed by '/crash_assistant crash jvm' command.");
                                try {
                                    UNSAFE.setMemory(0L, 1L, (byte) 0);
                                } catch (Throwable t) {
                                    CrashAssistant.LOGGER.error("Failed to crash JVM via Unsafe", t);
                                }
                            }
                        }
                    }).start();
                    return 0;
                }
            }
        } else {
            List<String> args = parseCrashArgs(context);
            boolean isNickname = !args.isEmpty() && Objects.equals(args.get(0), "nickname");
            String text = isNickname ? latestNickname : latestDiffText;
            if (text != null && !text.isEmpty()) {
                Minecraft.getInstance().addScheduledTask(() -> {
                    try {
                        Minecraft.getInstance().keyboardListener.setClipboardString(text);
                        TextComponentString okMsg = new TextComponentString(isNickname ? "Nickname copied to clipboard" : "Mod list diff copied to clipboard");
                        okMsg.setStyle(new Style().setColor(TextFormatting.GREEN));
                        sendClientMsg(okMsg);
                    } catch (Throwable var4x) {
                        TextComponentString errorMsg = new TextComponentString("Failed to copy to clipboard");
                        errorMsg.setStyle(new Style().setColor(TextFormatting.RED));
                        sendClientMsg(errorMsg);
                    }
                });
                return 0;
            } else {
                TextComponentString errorMsg = new TextComponentString(isNickname ? "No nickname available to copy" : "No diff text available to copy");
                errorMsg.setStyle(new Style().setColor(TextFormatting.RED));
                sendClientMsg(errorMsg);
                return 0;
            }
        }
    }

    public static boolean validateCrashArgs(List<String> args) {
        for (String arg : args) {
            if (!supportedCrashArgs.contains(arg)) {
                TextComponentString errorMsg = new TextComponentString(LanguageProvider.get("commands.crash_command_validation_failed") + " '" + arg + "'");
                errorMsg.setStyle(new Style().setColor(TextFormatting.RED));
                sendClientMsg(errorMsg);
                return false;
            }
        }

        return true;
    }

    public static List<String> parseCrashArgs(CommandContext<?> context) {
        List<String> args = new ArrayList<>();

        for (int i = 1; i <= supportedCrashArgs.size(); i++) {
            try {
                args.add((String)context.getArgument("arg" + i, String.class));
            } catch (IllegalArgumentException var4) {
                break;
            }
        }

        return args;
    }

    public static ArgumentBuilder getCrashArg(int i) {
        return RequiredArgumentBuilder.argument("arg" + i, StringArgumentType.string())
                .suggests(new CrashAssistantCommands.CrashArgsSuggestionProvider())
                .executes(CrashAssistantCommands::crash);
    }

    public static class ComponentModListDiffStringBuilder extends ModListDiffStringBuilder {
        ComponentModListDiffStringBuilder(ModListDiffStringBuilder modListDiffStringBuilder) {
            this.sb = modListDiffStringBuilder.sb;
        }

        public TextComponentString toComponent() {
            TextComponentString msg = new TextComponentString("");

            for (ModListDiffStringBuilder.ColoredString cs : this.sb) {
                TextComponentString part = new TextComponentString(cs.getText());
                if (!cs.getColor().isEmpty()) {
                    TextFormatting color = TextFormatting.valueOf(cs.getColor().toUpperCase());
                    part.setStyle(new Style().setColor(color));
                }

                msg.appendSibling(part);
                if (cs.isEndsWithNewLine()) {
                    msg.appendSibling(new TextComponentString("\n"));
                }
            }

            return msg;
        }
    }

    public static class CrashArgsSuggestionProvider<S> implements SuggestionProvider<S> {
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            List<String> existingArgs = CrashAssistantCommands.parseCrashArgs(context);

            for (String e : CrashAssistantCommands.supportedCrashArgs) {
                if (!existingArgs.contains(e) && (!Objects.equals(e, "--GCBeforeHeapDump") || existingArgs.contains("--withHeapDump"))) {
                    builder.suggest(e);
                }
            }

            return builder.buildFuture();
        }
    }

    public static class CrashCommandsSuggestionProvider<S> implements SuggestionProvider<S> {
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            for (String crashCommand : CrashAssistantCommands.supportedCrashCommands.keySet()) {
                builder.suggest(crashCommand);
            }

            return builder.buildFuture();
        }
    }
}
