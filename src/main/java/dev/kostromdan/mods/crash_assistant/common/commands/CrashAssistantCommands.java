package dev.kostromdan.mods.crash_assistant.common.commands;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.utils.HeapDumper;
import dev.kostromdan.mods.crash_assistant.common.utils.ThreadDumper;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiffStringBuilder;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.crash.CrashReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.IClientCommand;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

import static io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess.UNSAFE;


public class CrashAssistantCommands extends CommandBase implements IClientCommand {

    private static String latestDiffText = "";
    private static String latestNickname = "";
    private static final Map<String, String> SUPPORTED_CRASH_CMDS =
            new HashMap<String, String>() {{
                put("game", "Minecraft");
                put("jvm", "JVM");
                put("no_crash", "noCrash");
            }};

    private static final Set<String> SUPPORTED_CRASH_ARGS =
            new HashSet<>(Arrays.asList("--withThreadDump", "--withHeapDump", "--GCBeforeHeapDump"));

    private static Instant lastCrashCommand = Instant.EPOCH;

    @Override
    public String getName() {
        return "crash_assistant";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/crash_assistant <modlist|crash> …";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        LanguageProvider.updateLang();

        if (args.length == 0) {
            sendClientMsg(red(getUsage(sender)));
            return;
        }

        switch (args[0]) {
            case "modlist":
                handleModlist(Arrays.copyOfRange(args, 1, args.length));
                break;
            case "crash":
                handleCrash(Arrays.copyOfRange(args, 1, args.length));
                break;
            case "copy_to_clipboard":
                handleCopyToClipboard(Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                sendClientMsg(red(getUsage(sender)));
        }
    }

    private void handleModlist(String[] args) {
        if (!checkModlistFeatureEnabled()) return;

        if (args.length == 0) {
            sendClientMsg(red("/crash_assistant modlist <save|diff>"));
            return;
        }

        switch (args[0]) {
            case "save":
                saveModlist();
                break;
            case "diff":
                showDiff();
                break;
            default:
                sendClientMsg(red("/crash_assistant modlist <save|diff>"));
        }
    }

    private void saveModlist() {
        TextComponentString msg = new TextComponentString("");
        if (CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname)) {
            ModListUtils.saveCurrentModList();

            msg.appendSibling(green(LanguageProvider.get("commands.modlist_overwritten_success")));
            msg.appendSibling(new TextComponentString(" "));

            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update"))
                msg.appendSibling(white(LanguageProvider.get("commands.modlist_auto_update_msg")));
            else
                msg.appendSibling(white(LanguageProvider.get("commands.modlist_enable_auto_update_msg")));

            msg.appendSibling(getModConfigComponent());
        } else {
            msg.appendSibling(red(LanguageProvider.get("commands.not_creator_error_msg")));
            msg.appendSibling(getCopyNicknameComponent(CrashAssistant.playerNickname));
            msg.appendSibling(white(LanguageProvider.get("commands.add_to_creator_list_msg")));
            msg.appendSibling(getModConfigComponent());
        }
        sendClientMsg(msg);
    }

    private void showDiff() {
        ModListDiff diff = ModListDiff.getDiff(false);
        ModListDiffStringBuilder builder = diff.generateDiffMsg(false);
        ComponentModListDiffStringBuilder compBuilder = new ComponentModListDiffStringBuilder(builder);
        TextComponentString comp = compBuilder.toComponent();
        comp.appendSibling(getCopyDiffComponent(diff.generateDiffMsg(true)));
        sendClientMsg(comp);
    }

    private void handleCopyToClipboard(String[] args) {
        boolean isNickname = args.length > 0 && "nickname".equals(args[0]);

        if (isNickname) {
            if (latestNickname.isEmpty()) {
                sendClientMsg(red("No nickname available to copy"));
                return;
            }

            net.minecraft.client.gui.GuiScreen.setClipboardString(latestNickname);

            sendClientMsg(green("Nickname copied to clipboard"));
        } else {
            if (latestDiffText.isEmpty()) {
                sendClientMsg(red("No diff text available to copy"));
                return;
            }

            net.minecraft.client.gui.GuiScreen.setClipboardString(latestDiffText);

            sendClientMsg(green("Mod list diff copied to clipboard"));
        }
    }

    private void handleCrash(String[] args) {
        if (!CrashAssistantConfig.getBoolean("crash_command.enabled")) {
            sendClientMsg(red("Crash-command is disabled in the config."));
            return;
        }
        if (args.length == 0) {
            sendClientMsg(red("/crash_assistant crash <game|jvm|no_crash> [args]"));
            return;
        }

        String toCrashKey = args[0];
        if (!SUPPORTED_CRASH_CMDS.containsKey(toCrashKey)) {
            sendClientMsg(red(LanguageProvider.get("commands.crash_command_validation_failed_to_crash")
                    + " '" + toCrashKey + "'"));
            return;
        }

        String toCrash = SUPPORTED_CRASH_CMDS.get(toCrashKey);
        List<String> flags = Arrays.asList(args).subList(1, args.length);
        boolean noCrash = "noCrash".equals(toCrash);

        int seconds = CrashAssistantConfig.get("crash_command.seconds");

        if (seconds <= 0
                || Instant.now().isBefore(lastCrashCommand.plusSeconds(seconds))
                || noCrash) {
            if (!validateCrashArgs(flags)) return;
            new Thread(() -> actuallyCrash(toCrash, flags)).start();
            return;
        }

        lastCrashCommand = Instant.now();

        TextComponentString msg = new TextComponentString("");
        msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.crash_command_1")));
        msg.appendSibling(new TextComponentString(toCrash)
                .setStyle(new Style().setColor(TextFormatting.YELLOW)));
        msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.crash_command_2")));
        msg.appendSibling(new TextComponentString(Integer.toString(seconds))
                .setStyle(new Style().setColor(TextFormatting.YELLOW)));
        msg.appendSibling(new TextComponentString(LanguageProvider.get("commands.crash_command_3")))
                .setStyle(new Style().setColor(TextFormatting.RED));
        msg.setStyle(new Style().setColor(TextFormatting.RED));
        sendClientMsg(msg);
    }

    private void actuallyCrash(String toCrash, List<String> flags) {

        if (!flags.isEmpty()) {
            sendClientMsg(yellow(LanguageProvider.get("commands.crash_command_applying_args")));
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }

        if (flags.contains("--withThreadDump"))
            CrashAssistant.LOGGER.error("ThreadDump:\n" + ThreadDumper.obtainThreadDump());

        if (flags.contains("--withHeapDump")) {
            if (flags.contains("--GCBeforeHeapDump")) {
                CrashAssistant.LOGGER.info("GC before heap dump");
                System.gc();
            }
            try {
                CrashAssistant.LOGGER.error("Created heap dump at: " + HeapDumper.createHeapDump());
            } catch (Exception e) {
                CrashAssistant.LOGGER.error("Failed to create heap dump", e);
            }
        }

        if ("noCrash".equals(toCrash)) {
            sendClientMsg(green(LanguageProvider.get("commands.crash_command_done")));
            return;
        }

        sendClientMsg(red(LanguageProvider.get("commands.crash_command_crashing")));

        if ("Minecraft".equals(toCrash)) {
            Minecraft.getMinecraft().addScheduledTask(new Callable<Void>() {
                @Override
                public Void call() {
                    String reason = "Minecraft crashed by '/crash_assistant crash'";
                    CrashReport report = CrashReport.makeCrashReport(
                            new Throwable(reason), reason);
                    if (Minecraft.getMinecraft().world != null) {
                        Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(report);
                    }
                    Minecraft.getMinecraft().displayCrashReport(report);
                    return null;
                }
            });
        } else { // JVM
            CrashAssistant.LOGGER.error("JVM crashed by '/crash_assistant crash jvm'");
            UNSAFE.setMemory(0L, 1L, (byte) 0);
        }
    }

    private static boolean checkModlistFeatureEnabled() {
        LanguageProvider.updateLang();
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) return true;

        TextComponentString msg = red(LanguageProvider.get("commands.modlist_disabled_error_msg"));
        msg.appendSibling(getModConfigComponent());
        sendClientMsg(msg);
        return false;
    }

    private static boolean validateCrashArgs(List<String> args) {
        for (String arg : args) {
            if (!SUPPORTED_CRASH_ARGS.contains(arg)) {
                sendClientMsg(red(LanguageProvider.get("commands.crash_command_validation_failed") + " '" + arg + "'"));
                return false;
            }
        }
        return true;
    }

    public static void sendClientMsg(ITextComponent comp) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.ingameGUI.getChatGUI().printChatMessage(comp));
    }

    private static TextComponentString colored(String txt, TextFormatting fmt) {
        TextComponentString component = new TextComponentString(txt);
        component.setStyle(new Style().setColor(fmt));
        return component;
    }

    private static TextComponentString red(String txt) {
        return colored(txt, TextFormatting.RED);
    }

    private static TextComponentString green(String txt) {
        return colored(txt, TextFormatting.GREEN);
    }

    private static TextComponentString white(String txt) {
        return colored(txt, TextFormatting.WHITE);
    }

    private static TextComponentString yellow(String txt) {
        return colored(txt, TextFormatting.YELLOW);
    }

    public static ITextComponent getModConfigComponent() {
        return new TextComponentString("[mod config]")
                .setStyle(new Style()
                        .setColor(TextFormatting.YELLOW)
                        .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
                                CrashAssistantConfig.getConfigPath().toAbsolutePath().toString()))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new TextComponentString(LanguageProvider.get("commands.mod_config_tooltip")))));
    }

    public static ITextComponent getCopyNicknameComponent(String name) {
        latestNickname = name;

        return new TextComponentString("[nickname]")
                .setStyle(new Style()
                        .setColor(TextFormatting.YELLOW)
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crash_assistant copy_to_clipboard nickname"))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new TextComponentString(LanguageProvider.get("commands.nickname_tooltip")))));
    }

    public static ITextComponent getCopyDiffComponent(ModListDiffStringBuilder diff) {
        latestDiffText = diff.toText();

        return new TextComponentString("[" + LanguageProvider.get("commands.diff_copy") + "]")
                .setStyle(new Style()
                        .setColor(TextFormatting.YELLOW)
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crash_assistant copy_to_clipboard"))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new TextComponentString(LanguageProvider.get("commands.diff_tooltip")))));
    }

    public static class ComponentModListDiffStringBuilder extends ModListDiffStringBuilder {
        public ComponentModListDiffStringBuilder(ModListDiffStringBuilder sb) {
            this.sb = sb.sb;
        }

        public TextComponentString toComponent() {
            TextComponentString base = new TextComponentString("");
            for (ColoredString cs : sb) {
                TextComponentString part = new TextComponentString(cs.getText());
                if (!cs.getColor().isEmpty())
                    part.setStyle(new Style().setColor(TextFormatting.valueOf(cs.getColor().toUpperCase())));
                base.appendSibling(part);
                if (cs.isEndsWithNewLine()) base.appendSibling(new TextComponentString("\n"));
            }
            return base;
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable net.minecraft.util.math.BlockPos pos) {
        if (args.length == 1)
            return getListOfStringsMatchingLastWord(args, Arrays.asList("modlist", "crash"));

        if ("modlist".equals(args[0]) && args.length == 2)
            return getListOfStringsMatchingLastWord(args, Arrays.asList("save", "diff"));

        if ("crash".equals(args[0])) {
            if (args.length == 2)
                return getListOfStringsMatchingLastWord(args, SUPPORTED_CRASH_CMDS.keySet());

            List<String> left = new ArrayList<>(SUPPORTED_CRASH_ARGS);
            left.removeAll(Arrays.asList(args).subList(2, args.length));
            if (!Arrays.asList(args).contains("--withHeapDump"))
                left.remove("--GCBeforeHeapDump");
            return getListOfStringsMatchingLastWord(args, left);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean allowUsageWithoutPrefix(ICommandSender sender, String message) {
        return false;
    }
}
