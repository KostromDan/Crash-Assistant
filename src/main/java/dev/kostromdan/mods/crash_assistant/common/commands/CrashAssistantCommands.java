package dev.kostromdan.mods.crash_assistant.common.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.crash.CrashReport;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.utils.HeapDumper;
import dev.kostromdan.mods.crash_assistant.common.utils.ThreadDumper;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiffStringBuilder;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import sun.misc.Unsafe;

public class CrashAssistantCommands extends CommandBase {

    private static Unsafe UNSAFE;

    static {
        try {
            java.lang.reflect.Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            CrashAssistant.LOGGER.error("Failed to get Unsafe instance", e);
        }
    }

    private static String latestDiffText = "";
    private static String latestNickname = "";
    private static final Map<String, String> SUPPORTED_CRASH_CMDS = new HashMap<String, String>() {

        {
            put("game", "Minecraft");
            put("jvm", "JVM");
            put("no_crash", "noCrash");
        }
    };

    private static final Set<String> SUPPORTED_CRASH_ARGS = new HashSet<String>(
        Arrays.asList("--withThreadDump", "--withHeapDump", "--GCBeforeHeapDump"));

    private static long lastCrashCommandTime = 0L;

    @Override
    public String getCommandName() {
        return "crash_assistant";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/crash_assistant <modlist|crash> …";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        LanguageProvider.updateLang();

        if (args.length == 0) {
            sendClientMsg(red(getCommandUsage(sender)));
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
                sendClientMsg(red(getCommandUsage(sender)));
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
        ChatComponentText msg = new ChatComponentText("");
        if (CrashAssistantConfig.getModpackCreators()
            .contains(CrashAssistant.playerNickname)) {
            ModListUtils.saveCurrentModList();

            msg.appendSibling(green(LanguageProvider.get("commands.modlist_overwritten_success")));
            msg.appendSibling(new ChatComponentText(" "));

            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update"))
                msg.appendSibling(white(LanguageProvider.get("commands.modlist_auto_update_msg")));
            else msg.appendSibling(white(LanguageProvider.get("commands.modlist_enable_auto_update_msg")));

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
        ChatComponentText comp = compBuilder.toComponent();
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
            sendClientMsg(
                red(
                    LanguageProvider.get("commands.crash_command_validation_failed_to_crash") + " '"
                        + toCrashKey
                        + "'"));
            return;
        }

        String toCrash = SUPPORTED_CRASH_CMDS.get(toCrashKey);
        List<String> flags = Arrays.asList(args)
            .subList(1, args.length);
        boolean noCrash = "noCrash".equals(toCrash);

        int seconds = CrashAssistantConfig.get("crash_command.seconds");

        if (seconds <= 0 || System.currentTimeMillis() < lastCrashCommandTime + (seconds * 1000) || noCrash) {
            if (!validateCrashArgs(flags)) return;
            new Thread(new Runnable() {

                public void run() {
                    actuallyCrash(toCrash, flags);
                }
            }).start();
            return;
        }

        lastCrashCommandTime = System.currentTimeMillis();

        ChatComponentText msg = new ChatComponentText("");
        msg.appendSibling(new ChatComponentText(LanguageProvider.get("commands.crash_command_1")));

        ChatComponentText crashTypeText = new ChatComponentText(toCrash);
        ChatStyle crashTypeStyle = new ChatStyle();
        crashTypeStyle.setColor(EnumChatFormatting.YELLOW);
        crashTypeText.setChatStyle(crashTypeStyle);
        msg.appendSibling(crashTypeText);

        msg.appendSibling(new ChatComponentText(LanguageProvider.get("commands.crash_command_2")));

        ChatComponentText secondsText = new ChatComponentText(Integer.toString(seconds));
        ChatStyle secondsStyle = new ChatStyle();
        secondsStyle.setColor(EnumChatFormatting.YELLOW);
        secondsText.setChatStyle(secondsStyle);
        msg.appendSibling(secondsText);

        ChatComponentText part3 = new ChatComponentText(LanguageProvider.get("commands.crash_command_3"));
        ChatStyle part3Style = new ChatStyle();
        part3Style.setColor(EnumChatFormatting.RED);
        part3.setChatStyle(part3Style);
        msg.appendSibling(part3);

        ChatStyle msgStyle = new ChatStyle();
        msgStyle.setColor(EnumChatFormatting.RED);
        msg.setChatStyle(msgStyle);

        sendClientMsg(msg);
    }

    private void actuallyCrash(String toCrash, List<String> flags) {

        if (!flags.isEmpty()) {
            sendClientMsg(yellow(LanguageProvider.get("commands.crash_command_applying_args")));
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
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
            // In 1.7.10, we can't use scheduleTask, so we'll create a crash directly
            String reason = "Minecraft crashed by '/crash_assistant crash'";
            CrashReport report = CrashReport.makeCrashReport(new Throwable(reason), reason);
            if (Minecraft.getMinecraft().theWorld != null) {
                Minecraft.getMinecraft()
                    .addGraphicsAndWorldToCrashReport(report);
            }
            Minecraft.getMinecraft()
                .displayCrashReport(report);
        } else { // JVM
            CrashAssistant.LOGGER.error("JVM crashed by '/crash_assistant crash jvm'");
            UNSAFE.setMemory(0L, 1L, (byte) 0);
        }
    }

    private static boolean checkModlistFeatureEnabled() {
        LanguageProvider.updateLang();
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) return true;

        ChatComponentText msg = red(LanguageProvider.get("commands.modlist_disabled_error_msg"));
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

    public static void sendClientMsg(IChatComponent root) {
        Minecraft mc = Minecraft.getMinecraft();
        for (IChatComponent line : splitIntoLines(root)) {
            mc.ingameGUI.getChatGUI()
                .printChatMessage(line);
        }
    }

    private static List<IChatComponent> splitIntoLines(IChatComponent root) {
        LineBuilder lb = new LineBuilder();
        walk(root, lb);
        lb.finishCurrent();
        return lb.lines;
    }

    @SuppressWarnings("unchecked")
    private static void walk(IChatComponent comp, LineBuilder lb) {

        if (comp instanceof ChatComponentText) {
            ChatComponentText txt = (ChatComponentText) comp;
            String[] parts = txt.getChatComponentText_TextValue()
                .split("\\n", -1);

            for (int i = 0; i < parts.length; i++) {
                if (i > 0) lb.newLine();

                if (!parts[i].isEmpty()) {
                    ChatComponentText frag = new ChatComponentText(parts[i]);
                    frag.setChatStyle(txt.getChatStyle());
                    lb.current.appendSibling(frag);
                }
            }
        } else {
            lb.current.appendSibling(comp.createCopy());
        }

        for (IChatComponent sib : (List<IChatComponent>) comp.getSiblings()) {
            walk(sib, lb);
        }
    }

    private static class LineBuilder {

        final List<IChatComponent> lines = new ArrayList<IChatComponent>();
        ChatComponentText current = new ChatComponentText("");

        void newLine() {
            lines.add(current);
            current = new ChatComponentText("");
        }

        void finishCurrent() {
            lines.add(current);
        }
    }

    private static ChatComponentText colored(String txt, EnumChatFormatting fmt) {
        ChatComponentText component = new ChatComponentText(txt);
        component.getChatStyle()
            .setColor(fmt);
        return component;
    }

    private static ChatComponentText red(String txt) {
        return colored(txt, EnumChatFormatting.RED);
    }

    private static ChatComponentText green(String txt) {
        return colored(txt, EnumChatFormatting.GREEN);
    }

    private static ChatComponentText white(String txt) {
        return colored(txt, EnumChatFormatting.WHITE);
    }

    private static ChatComponentText yellow(String txt) {
        return colored(txt, EnumChatFormatting.YELLOW);
    }

    public static IChatComponent getModConfigComponent() {
        ChatComponentText component = new ChatComponentText("[mod config]");
        ChatStyle style = new ChatStyle();
        style.setColor(EnumChatFormatting.YELLOW);
        style.setChatClickEvent(
            new ClickEvent(
                ClickEvent.Action.OPEN_FILE,
                CrashAssistantConfig.getConfigPath()
                    .toAbsolutePath()
                    .toString()));
        style.setChatHoverEvent(
            new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText(LanguageProvider.get("commands.mod_config_tooltip"))));
        component.setChatStyle(style);
        return component;
    }

    public static IChatComponent getCopyNicknameComponent(String name) {
        latestNickname = name;

        ChatComponentText component = new ChatComponentText("[nickname]");
        ChatStyle style = new ChatStyle();
        style.setColor(EnumChatFormatting.YELLOW);
        style.setChatClickEvent(
            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crash_assistant copy_to_clipboard nickname"));
        style.setChatHoverEvent(
            new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText(LanguageProvider.get("commands.nickname_tooltip"))));
        component.setChatStyle(style);
        return component;
    }

    public static IChatComponent getCopyDiffComponent(ModListDiffStringBuilder diff) {
        latestDiffText = diff.toText();

        ChatComponentText component = new ChatComponentText("[" + LanguageProvider.get("commands.diff_copy") + "]");
        ChatStyle style = new ChatStyle();
        style.setColor(EnumChatFormatting.YELLOW);
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crash_assistant copy_to_clipboard"));
        style.setChatHoverEvent(
            new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText(LanguageProvider.get("commands.diff_tooltip"))));
        component.setChatStyle(style);
        return component;
    }

    public static class ComponentModListDiffStringBuilder extends ModListDiffStringBuilder {

        public ComponentModListDiffStringBuilder(ModListDiffStringBuilder sb) {
            this.sb = sb.sb;
        }

        public ChatComponentText toComponent() {
            ChatComponentText base = new ChatComponentText("");
            for (ColoredString cs : sb) {
                ChatComponentText part = new ChatComponentText(cs.getText());
                if (!cs.getColor()
                    .isEmpty()) {
                    ChatStyle style = new ChatStyle();
                    style.setColor(
                        EnumChatFormatting.valueOf(
                            cs.getColor()
                                .toUpperCase()));
                    part.setChatStyle(style);
                }
                base.appendSibling(part);
                if (cs.isEndsWithNewLine()) base.appendSibling(new ChatComponentText("\n"));
            }
            return base;
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, new String[] { "modlist", "crash" });

        if ("modlist".equals(args[0]) && args.length == 2)
            return getListOfStringsMatchingLastWord(args, new String[] { "save", "diff" });

        if ("crash".equals(args[0])) {
            if (args.length == 2) return getListOfStringsMatchingLastWord(
                args,
                SUPPORTED_CRASH_CMDS.keySet()
                    .toArray(new String[0]));

            List<String> left = new ArrayList<String>(SUPPORTED_CRASH_ARGS);
            left.removeAll(
                Arrays.asList(args)
                    .subList(2, args.length));
            if (!Arrays.asList(args)
                .contains("--withHeapDump")) left.remove("--GCBeforeHeapDump");
            return getListOfStringsMatchingLastWord(args, left.toArray(new String[0]));
        }
        return Collections.emptyList();
    }
}
