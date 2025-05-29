package dev.kostromdan.mods.crash_assistant.common.commands;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.utils.HeapDumper;
import dev.kostromdan.mods.crash_assistant.common.utils.ManualCrashThrower;
import dev.kostromdan.mods.crash_assistant.common.utils.ThreadDumper;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiffStringBuilder;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CrashAssistantCommands {
    public static final HashMap<String, String> supportedCrashCommands = new HashMap<String, String>() {{
        put("game", "Minecraft");
        put("jvm", "JVM");
        put("no_crash", "noCrash");
    }};
    public static final HashSet<String> supportedCrashArgs = new HashSet<String>() {{
        add("--withThreadDump");
        add("--withHeapDump");
        add("--GCBeforeHeapDump");
    }};
    public static Instant lastCrashCommand = Instant.ofEpochMilli(0);
    public static boolean isDeadLocked = false;

    @SuppressWarnings("unchecked")
    public static <S> LiteralArgumentBuilder<S> getCommands() {
        return LiteralArgumentBuilder.literal("crash_assistant")
                .then(LiteralArgumentBuilder.literal("modlist")
                        .then(LiteralArgumentBuilder.literal("save")
                                .executes(CrashAssistantCommands::saveModlist)
                        ).then(LiteralArgumentBuilder.literal("diff")
                                .executes(CrashAssistantCommands::showDiff)
                        ))
                .then(LiteralArgumentBuilder.literal("crash")
                        .requires(c -> CrashAssistantConfig.getBoolean("crash_command.enabled"))
                        .then(RequiredArgumentBuilder.argument("to_crash", StringArgumentType.string())
                                .suggests(new CrashCommandsSuggestionProvider<>())
                                .executes(CrashAssistantCommands::crash)
                                .then(getCrashArg(1)
                                        .then(getCrashArg(2)
                                                .then(getCrashArg(3))))));
    }

    public static Component getModConfigComponent() {
        TextComponent component = new TextComponent("[mod config]");
        Style style = Style.EMPTY
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, CrashAssistantConfig.getConfigPath().toAbsolutePath().toString()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(LanguageProvider.get("commands.mod_config_tooltip"))));
        component.setStyle(style);
        return component;
    }

    public static Component getCopyNicknameComponent(String playerNickname) {
        TextComponent component = new TextComponent("[nickname]");
        Style style = Style.EMPTY
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, '"' + playerNickname + '"'))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(LanguageProvider.get("commands.nickname_tooltip"))));
        component.setStyle(style);
        return component;
    }

    public static Component getCopyDiffComponent(ModListDiffStringBuilder diff) {
        TextComponent component = new TextComponent("[" + LanguageProvider.get("commands.diff_copy") + "]");
        Style style = Style.EMPTY
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, diff.toText()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(LanguageProvider.get("commands.diff_tooltip"))));
        component.setStyle(style);
        return component;
    }

    public static void sendClientMsg(Component message) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().gui.getChat().addMessage(message);
        });
    }

    public static boolean checkModlistFeatureEnabled() {
        LanguageProvider.updateLang();
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            return true;
        }
        TextComponent msg = new TextComponent("");
        msg.append(new TextComponent(LanguageProvider.get("commands.modlist_disabled_error_msg")));
        msg.append(getModConfigComponent());
        msg.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        sendClientMsg(msg);
        return false;
    }

    public static int saveModlist(CommandContext<?> context) {
        if (!checkModlistFeatureEnabled()) {
            return 0;
        }

        TextComponent msg = new TextComponent("");
        if (CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname)) {
            ModListUtils.saveCurrentModList();
            msg.append(new TextComponent(LanguageProvider.get("commands.modlist_overwritten_success")));
            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update")) {
                TextComponent autoUpdateMsg = new TextComponent(LanguageProvider.get("commands.modlist_auto_update_msg"));
                autoUpdateMsg.setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
                msg.append(autoUpdateMsg);
            } else {
                TextComponent enableAutoUpdateMsg = new TextComponent(LanguageProvider.get("commands.modlist_enable_auto_update_msg"));
                enableAutoUpdateMsg.setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
                msg.append(enableAutoUpdateMsg);
            }
            msg.append(getModConfigComponent());
            msg.setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN));
        } else {
            msg.append(new TextComponent(LanguageProvider.get("commands.not_creator_error_msg")));
            msg.append(getCopyNicknameComponent(CrashAssistant.playerNickname));
            msg.append(new TextComponent(LanguageProvider.get("commands.add_to_creator_list_msg")));
            msg.append(getModConfigComponent());
            msg.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        }

        sendClientMsg(msg);
        return 0;
    }

    public static int showDiff(CommandContext<?> context) {
        if (!checkModlistFeatureEnabled()) {
            return 0;
        }
        ModListDiff diff = ModListDiff.getDiff(false);
        TextComponent msg = new ComponentModListDiffStringBuilder(diff.generateDiffMsg(false)).toComponent();
        msg.append(getCopyDiffComponent(diff.generateDiffMsg(true)));
        sendClientMsg(msg);
        return 0;
    }

    private static int deadlockIntegratedServer(CommandContext<?> context) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            isDeadLocked = true;
            while (isDeadLocked) {
            }
        });
        return 0;
    }

    private static int releaseIntegratedServer(CommandContext<?> context) {
        isDeadLocked = false;
        return 0;
    }

    public static int crash(CommandContext<?> context) {
        LanguageProvider.updateLang();
        TextComponent msg = new TextComponent("");
        String toCrash = "null";
        try {
            toCrash = context.getArgument("to_crash", String.class);
            if (!supportedCrashCommands.containsKey(toCrash)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ignored) {
            TextComponent errorMsg = new TextComponent(LanguageProvider.get("commands.crash_command_validation_failed_to_crash") + " '" + toCrash + "'");
            errorMsg.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
            sendClientMsg(errorMsg);
            return 0;
        }
        toCrash = supportedCrashCommands.get(toCrash);

        int secondsToCrash = CrashAssistantConfig.get("crash_command.seconds");
        boolean noCrash = Objects.equals(toCrash, "noCrash");
        if (secondsToCrash <= 0 || Instant.now().isBefore(lastCrashCommand.plusSeconds(secondsToCrash)) || noCrash) {
            List<String> args = parseCrashArgs(context);
            String finalToCrash = toCrash;
            new Thread(() -> {
                if (!validateCrashArgs(args)) return;
                if (!args.isEmpty()) {
                    TextComponent applyingArgsMsg = new TextComponent(LanguageProvider.get("commands.crash_command_applying_args"));
                    applyingArgsMsg.setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
                    sendClientMsg(applyingArgsMsg);
                    try {
                        Thread.sleep(100); // Wait while main thread sends msg, since next operations are blocking.
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
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
                    } catch (Exception e) {
                        CrashAssistant.LOGGER.error("Failed to create heap dump.", e);
                    }
                }

                if (!noCrash) {
                    TextComponent crashingMsg = new TextComponent(LanguageProvider.get("commands.crash_command_crashing"));
                    crashingMsg.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
                    sendClientMsg(crashingMsg);
                } else {
                    sendClientMsg(new TextComponent(LanguageProvider.get("commands.crash_command_done")));
                }

                if (Objects.equals(finalToCrash, "Minecraft")) {
                    Minecraft.getInstance().execute(() -> {
                        ManualCrashThrower.crashGame("Minecraft crashed by '/crash_assistant crash' command.");
                    });
                } else if (Objects.equals(finalToCrash, "JVM")) {
                    CrashAssistant.LOGGER.error("JVM crashed by '/crash_assistant crash jvm' command.");
                    Blaze3D.youJustLostTheGame();
                }
            }).start();
            return 0;
        }
        lastCrashCommand = Instant.now();

        msg.append(new TextComponent(LanguageProvider.get("commands.crash_command_1")));
        TextComponent toCrashComponent = new TextComponent(toCrash);
        toCrashComponent.setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        msg.append(toCrashComponent);
        msg.append(new TextComponent(LanguageProvider.get("commands.crash_command_2")));
        TextComponent secondsComponent = new TextComponent(Integer.toString(secondsToCrash));
        secondsComponent.setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        msg.append(secondsComponent);
        msg.append(new TextComponent(LanguageProvider.get("commands.crash_command_3")));
        msg.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        sendClientMsg(msg);
        return 0;
    }

    public static boolean validateCrashArgs(List<String> args) {
        for (String arg : args) {
            if (!supportedCrashArgs.contains(arg)) {
                TextComponent errorMsg = new TextComponent(LanguageProvider.get("commands.crash_command_validation_failed") + " '" + arg + "'");
                errorMsg.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
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
                args.add(context.getArgument("arg" + i, String.class));
            } catch (IllegalArgumentException ignored) {
                break;
            }
        }
        return args;
    }

    public static ArgumentBuilder getCrashArg(int i) {
        return RequiredArgumentBuilder.argument("arg" + i, StringArgumentType.string())
                .suggests(new CrashArgsSuggestionProvider<>())
                .executes(CrashAssistantCommands::crash);
    }

    public static class CrashArgsSuggestionProvider<S> implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            List<String> existingArgs = parseCrashArgs(context);
            for (String e : supportedCrashArgs) {
                if (existingArgs.contains(e)) continue;
                if (Objects.equals(e, "--GCBeforeHeapDump") && !existingArgs.contains("--withHeapDump")) continue;
                builder.suggest(e);
            }
            return builder.buildFuture();
        }
    }

    public static class CrashCommandsSuggestionProvider<S> implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            for (String crashCommand : supportedCrashCommands.keySet()) {
                builder.suggest(crashCommand);
            }
            return builder.buildFuture();
        }
    }

    public static class ComponentModListDiffStringBuilder extends ModListDiffStringBuilder {
        ComponentModListDiffStringBuilder(ModListDiffStringBuilder modListDiffStringBuilder) {
            this.sb = modListDiffStringBuilder.sb;
        }

        public TextComponent toComponent() {
            TextComponent msg = new TextComponent("");
            for (ColoredString cs : sb) {
                TextComponent part = new TextComponent(cs.getText());
                if (!cs.getColor().isEmpty()) {
                    ChatFormatting color = ChatFormatting.valueOf(cs.getColor().toUpperCase());
                    part.setStyle(Style.EMPTY.withColor(color));
                }
                msg.append(part);
                if (cs.isEndsWithNewLine()) {
                    msg.append(new TextComponent("\n"));
                }
            }
            return msg;
        }
    }
}
