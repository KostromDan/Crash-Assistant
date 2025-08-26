package dev.kostromdan.mods.crash_assistant.common_config.config;

import com.electronwill.nightconfig.core.AbstractCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;
import dev.kostromdan.mods.crash_assistant.common_config.lang.Lang;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class CrashAssistantConfig {
    private static final Path CONFIG_PATH = Paths.get("config", "crash_assistant", "config.toml").toAbsolutePath().normalize();
    private static final Path CONFIG_LOCK_PATH = Paths.get("local", "crash_assistant", "CONFIG_LOCK.tmp").toAbsolutePath().normalize();
    private static final Logger LOGGER = LogManager.getLogger();

    private static CommentedFileConfig config;
    private static final HashSet<String> usedOptions = new HashSet<>();
    private static long lastConfigUpdate;

    static {
        executeWithLock(() -> {
            config = CommentedFileConfig.builder(CONFIG_PATH, TomlFormat.instance())
                    .preserveInsertionOrder()
                    .build();
            load();
        });
    }

    private static void setupDefaultValues() {
        usedOptions.clear();
        config.setComment("general", "General settings of Crash Assistant mod.");
        if (Objects.equals(config.get("general.help_link"), "https://discord.gg/moddedmc")) {
            config.remove("general.help_link");
        }
        addOption("general.help_link",
                "Link which will be opened in browser on request_help_button pressed.\n" +
                        "If equals CHANGE_ME, will open Forge/NeoForge/Fabric/Quilt discord link. Names of communities/channels also will be used not from config, but according to this link.\n" +
                        "Must start with 'https://' or 'www.'",
                "CHANGE_ME");
        addOption("general.upload_to",
                "Anyways log will be uploaded to mclo.gs, but with this option you can wrap link to gnomebot.dev for better formatting.\n" +
                        "If help_link equals 'CHANGE_ME', this value will be ignored and gnomebot.dev used.\n" +
                        "Supported values: mclo.gs / gnomebot.dev",
                "gnomebot.dev");
        addOption("general.show_on_fml_error_screen",
                "Show gui on minecraft crashed on modloading and FML error screen displayed.",
                true);
        addOption("general.kill_old_app",
                "Close old CrashAssistantApp if it's still running when starting a new instance of Minecraft, to avoid confusing player with window from old crash.",
                true);
        addOption("general.default_lang",
                "If options.txt doesn't exist, the default language will be used.",
                "en_us");
        addOption("general.enable_privacy_policy_acceptance",
                "Before uploading the first log, requires the user to accept the privacy policy.\n" +
                        "Disabling this option may be illegal in some countries if you are modpack creator. Disable at your own risk.",
                true);
        ArrayList<String> defaultBlacklistedLogs = new ArrayList<>();
        addOption("general.blacklisted_logs",
                "List of blacklisted log files (checked with startswith()). This files won't show in GUI logs list.",
                defaultBlacklistedLogs);
        List<String> blacklistedLogs = config.get("general.blacklisted_logs");
        if (blacklistedLogs.contains("CrashAssistant: latest.log")) {
            blacklistedLogs.remove("CrashAssistant: latest.log");
            config.set("general.blacklisted_logs", blacklistedLogs);
        }

        config.setComment("text", "Here you can change text of lang placeHolders.\n" +
                "Also you can change any text in lang files.\n" +
                "You don't need to modify jar. You can change it in config/crash_assistant/lang. For more info read README.md file located where.");
        if (Objects.equals(config.get("text.support_name"), "Modded Minecraft Discord") || Objects.equals(config.get("text.support_name"), "mod loader Discord")) {
            config.remove("text.support_name");
        }
        addOption("text.support_name",
                "$CONFIG.text.support_name$ in lang files will be replaced with this value.\n" +
                        "For example this placeHolder used in: \"Request help in the $CONFIG.text.support_name$\"",
                "example Discord");
        addOption("text.support_place",
                "$CONFIG.text.support_place$ in lang files will be replaced with this value.",
                "#example channel");
        addOption("text.modpack_name",
                "$CONFIG.text.modpack_name$ in lang files will be replaced with this value.\n" +
                        "For example this placeHolder used in: \"Oops, $CONFIG.text.modpack_name$ crashed!\"\n" +
                        "Supports Better Compatibility Checker integration. You can use $BCC.modpackName$, $BCC.modpackVersion$, etc and it will be replaced with value from BCC config.",
                "Minecraft");

        config.setComment("generated_message", "Settings of message generated by Upload all button");
        addOption("generated_message.h3_prefix",
                "Add ### prefix before filename.\n" +
                        "This can prevent too small, hard to hit on mobile links.",
                true);
        addOption("generated_message.one_line_logs",
                "Replaces \"\\n\" separator between logs to \"   |   \" to make message vertically smaller.",
                true);
        addOption("generated_message.intel_corrupted_notification",
                "Adds line in log list about this Intel processor can be corrupted.",
                true);
        addOption("generated_message.generated_msg_lang",
                "If the modpack is created for a non-English-speaking audience, сhange this to the language the modpack is designed for.\n" +
                        "This lang will be used only for generating message by \"Upload all...\" button." +
                        "Do not modify this value if there's a chance that the generated message will be sent to English-speaking communities.",
                "en_us");
        addOption("generated_message.text_under_crashed",
                "This text will be under \"$CONFIG.text.modpack_name$ crashed!\" in generated message by Upload all button.\n" +
                        "You can include:\n" +
                        "   * some form, which users should fill out.\n" +
                        "   * additional information like Minecraft version, etc.",
                "");
        addOption("generated_message.warning_after_upload_all_button_press",
                "With this option you can notify user about something related with posting generated message.\n" +
                        "For example if they need to fill some option from \"text_under_crashed\", etc.\n" +
                        "Supports html formatting, placeholders.\n" +
                        "Leave empty to prevent showing this warning message.",
                "");
        addOption("generated_message.put_problematic_frame_to_message",
                "Puts problematic frame from hs_err to message.",
                true);
        addOption("generated_message.put_analysis_result_to_message",
                "Puts analysis result(names of crash reasons) to message, instead of just count.",
                true);
        addOption("generated_message.color_message",
                "Color modified mods count/analysis in msg with ANSI.\n" +
                        "Can be needed to be disabled if issues are reported to something not supporting ANSI codeblocks, like GitHub.",
                true);

        config.setComment("modpack_modlist", "Settings of modlist feature.\n" +
                "Adds in generated msg block about which mods modpack user added/removed/updated.\n" +
                "Also you can see diff by running '/crash_assistant modlist diff' command.");
        addOption("modpack_modlist.enabled",
                "Enable feature.",
                true);
        addOption("modpack_modlist.modpack_creators",
                "nicknames of players, who considered as modpack creator.\n" +
                        "Only this players can overwrite modlist.json\n" +
                        "If this feature is enabled and this array is empty, will be appended with nickname of current player.\n" +
                        "-----------------------------------------------------------------------------------------------------\n" +
                        "Warning! This is not displayed anywhere, it's just tech param used for modlist feature to work correctly.\n" +
                        "Here must be actual nicknames of people who work with the modpack and publishing releases!\n" +
                        "-----------------------------------------------------------------------------------------------------",
                new ArrayList<String>());
        addOption("modpack_modlist.auto_update",
                "If enabled, modlist.json will be overwritten on every launch(first tick of TitleScreen),\n" +
                        "then game is launched by modpack creator.\n" +
                        "So you won't forget to save it before publishing.\n" +
                        "If you want to save manually: disable this and use '/crash_assistant modlist save' command.",
                true);
        addOption("modpack_modlist.add_resourcepacks",
                "If enabled, will add resourcepacks to modlist.json\n" +
                        "After filename where will be ' (resourcepack)' suffix.",
                false);
        addOption("modpack_modlist.add_modloader_jar_name",
                "If enabled, will add modloader jar name to modlist, to easily track if user changed version of modloader.",
                true);
        addOption("modpack_modlist.add_modlist_txt_as_log",
                "If enabled, will add generated modlist.txt, with names of all mods / modids / mixin configs / jarjar mods info to logs.",
                true);

        config.setComment("analysis", "Settings of analysis feature.\n" +
                "Analysing logs for most common reasons of crashes and displaying recommendations with fixes.");
        addOption("analysis.enabled",
                "Enable feature.",
                true);
        addOption("analysis.blacklisted_reasons",
                "Here you can disable some Analysis by class names.\n" +
                        "List of them can be found here: dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons\n" +
                        "For example \"Create6Addons\"",
                new ArrayList<>());

        config.setComment("crash_command", "Settings of '/crash_assistant crash' command feature.");
        addOption("crash_command.enabled",
                "Enable feature.",
                true);
        addOption("crash_command.seconds",
                "To ensure the user really wants to crash the game, the command needs to be run again within this amount of seconds.\n" +
                        "Set to <= 0 to disable the confirmation.",
                10);

        config.setComment("intel_corrupted", "Settings of notifying about intel corrupted processors.");
        addOption("intel_corrupted.enabled",
                "Enable feature.",
                true);
        addOption("intel_corrupted.show_gif",
                "Show funny related gif in warning message.",
                true);

        addOption("greeting.shown_greeting",
                "You don't need to touch this option.\n" +
                        "On first world join of modpack creator if set to false shows greeting, then self enables.",
                false);

        config.setComment("gui_customisation", "You can customise GUI with this options.");
        addOption("gui_customisation.show_dont_send_screenshot_of_gui_notice",
                "Append comment text with notice about sending screenshot of this gui tells nothing to modpack creators.",
                true);
        addOption("gui_customisation.screenshot_of_gui_notice_animated_border",
                "Animate border to request user attention even more.",
                true);
        addOption("gui_customisation.upload_all_button_font_size",
                "You can make Upload All Button bigger/smaller to request user attention.\n" +
                        "Default Swing font size is 12, Default for this button in crash assistant is 16.\n" +
                        "Not recommended to set it more than 16, as it will affect the increase of GUI size because all text won't fit.",
                16);
        addOption("gui_customisation.request_help_button_font_size",
                "Same as upload_all_button_font_size, but for Request Help button.",
                16);
        addOption("gui_customisation.upload_all_button_foreground_color",
                "You can change Upload All Button color to request user attention.\n" +
                        "format is \"R_G_B\", range is 0-255, for example \"255_0_0\" is red color. Use \"default\" to use default swing color.\n" +
                        "Default for this button is \"0_178_0\" (dark green color).",
                "0_178_0");
        addOption("gui_customisation.request_help_button_foreground_color",
                "Same as upload_all_button_foreground_color, but for Request Help button.\n" +
                        "Default for this button is \"0_0_178\" (dark blue color).",
                "0_0_178");
//        addOption("gui_customisation.upload_all_button_background_color",
//                "You can change Upload All Button background color to request user attention.\n" +
//                        "format is same as in upload_all_button_color.\n" +
//                        "Default for this button is \"255_110_0\" (dark orange color).",
//                "238_238_238");
//        addOption("gui_customisation.request_help_button_background_color",
//                "Same as upload_all_button_background_color, but for Request Help button.\n" +
//                        "Default for this button is \"0_0_0\" (black color).",
//                "238_238_238");

        config.setComment("compatibility", "Checks crash_assistant compatibility with other incompatible mods.\n" +
                "Highly unrecommended to disable!");
        addOption("compatibility.enabled",
                "Enable feature.",
                true);


        HashSet<String> toRemove = new HashSet<>();
        config.valueMap().forEach((key, value) -> {
            if (value instanceof AbstractCommentedConfig) {
                ((AbstractCommentedConfig) value).valueMap().forEach((k, v) -> {
                    String mergedKey = key + "." + k;
                    if (!usedOptions.contains(mergedKey)) {
                        toRemove.add(mergedKey);
                    }
                });
            }
            if (!usedOptions.contains(key)) {
                toRemove.add(key);
            }
        });
        toRemove.forEach(key -> {
            config.remove(key);
            LOGGER.warn("Removed config option due to it not used in config anymore: " + key);
        });
    }

    private static <T> void addOption(String path, String comment, T defaultValue) {
        usedOptions.add(path);
        usedOptions.add(path.split("\\.")[0]);
        config.setComment(path, comment);
        if (!config.contains(path)) {
            config.set(path, defaultValue);
        } else if (config.get(path).getClass() != defaultValue.getClass()) {
            LOGGER.warn("Error while reading config param: '" + path + "'. Current value class:'" + config.get(path).getClass().getName() + "' is not equal to needed:'" + defaultValue.getClass().getName() + "'. Resetting to default!");
            config.set(path, defaultValue);
        }
    }

    public static ArrayList<String> getBlacklistedLogs() {
        return get("general.blacklisted_logs");
    }

    public static ArrayList<String> getBlacklistedAnalysis() {
        return get("analysis.blacklisted_reasons");
    }

    public static ArrayList<String> getModpackCreators() {
        return get("modpack_modlist.modpack_creators");
    }

    public static void addModpackCreator(String nickname) {
        ArrayList<String> currentModpackCreators = getModpackCreators();
        currentModpackCreators.add(nickname);
        set("modpack_modlist.modpack_creators", currentModpackCreators);
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }


    public static void executeWithLock(Runnable body) {
        Exception ex = null;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.createDirectories(CONFIG_LOCK_PATH.getParent());
            try {
                Files.createFile(CONFIG_LOCK_PATH);
            } catch (FileAlreadyExistsException ignored) {
            }
            try (FileChannel ch = FileChannel.open(CONFIG_LOCK_PATH, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = ch.lock()) {
                try {
                    body.run();
                    return;
                } catch (Exception e) {
                    ex = e;
                }
            }
        } catch (OverlappingFileLockException e) {   // already locked in *this* JVM
            body.run();                              // just run without new lock
        } catch (NoSuchFileException e) {
            LOGGER.warn("Cursed extreme rare issue! Maybe bug in JVM or FileSystem! File is missing, despite the fact we just created it. Dropping config access synchronisation logic! Issues may arise!", e);
            body.run();
        } catch (IOException e) {
            throw new RuntimeException("Could not create or lock " + CONFIG_LOCK_PATH, e);
        }
        if (ex != null) {
            throw new RuntimeException("Exception while executing executeWithLock block:", ex);
        }
    }

    public static void update() {
        executeWithLock(() -> {
            if (!CONFIG_PATH.toFile().exists() || CONFIG_PATH.toFile().lastModified() > lastConfigUpdate) {
                load();
            }
        });
    }

    public static void load() {
        executeWithLock(() -> {
            try {
                config.load();
            } catch (ParsingException e) {
                LOGGER.error("Error while loading config, saved old problematic config as 'config.toml.bak', resetting 'config.toml' to default values:", e);
                try {
                    CONFIG_PATH.toFile().renameTo(Paths.get(CONFIG_PATH.getParent().toString(), "config.toml.bak").toFile());
                } catch (Exception e1) {
                    LOGGER.error("Failed to rename 'config.toml' to 'config.toml.bak': ", e1);
                }
                config.clear();
            }
            int old_values_hash = config.valueMap().hashCode();
            long old_comments_hash = getCommentsHash();
            setupDefaultValues();
            if (config.valueMap().hashCode() != old_values_hash || getCommentsHash() != old_comments_hash) {
                save();
            }
            lastConfigUpdate = CONFIG_PATH.toFile().lastModified();
        });
    }

    public static long getCommentsHash() {
        long hash = 0;
        hash += config.commentMap().hashCode();
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof AbstractCommentedConfig) {
                hash += ((AbstractCommentedConfig) value).commentMap().hashCode();
            }
        }
        return hash;
    }

    public static void save() {
        executeWithLock(() -> {
            config.save();
            lastConfigUpdate = CONFIG_PATH.toFile().lastModified();
        });
    }

    public static synchronized <T> T get(String path) {
        final AtomicReference<T> result = new AtomicReference<>();
        executeWithLock(() -> {
            update();
            result.set(config.get(path));
        });
        return result.get();
    }

    public static boolean getBoolean(String path) {
        return get(path);
    }

    public static int getInteger(String path) {
        return get(path);
    }

    public static String get(String path, boolean applyPlaceHolders) {
        return applyPlaceHolders ? Lang.applyPlaceHolders(config.get(path), new HashMap<>()) : config.get(path);
    }

    public static <T> void set(String path, T value) {
        executeWithLock(() -> {
            update();
            config.set(path, value);
            save();
        });
    }

    public static void main(String[] args) { // Debug config.
    }
}
