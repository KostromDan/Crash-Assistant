package dev.kostromdan.mods.crash_assistant.config;

import com.electronwill.nightconfig.core.AbstractCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import dev.kostromdan.mods.crash_assistant.lang.Lang;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class CrashAssistantConfig {
    private static final Path CONFIG_PATH = Paths.get("config", "crash_assistant", "config.toml");
    private static final Path CONFIG_LOCK_PATH = Paths.get("local", "crash_assistant", "CONFIG_LOCK.tmp");
    private static final Logger LOGGER = LogManager.getLogger();

    private static CommentedFileConfig config;
    private static final HashSet<String> usedOptions = new HashSet<>();
    private static long lastConfigUpdate;

    static {
        executeWithLock(() -> {
            config = CommentedFileConfig.builder(CONFIG_PATH)
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
        addOption("general.show_dont_send_screenshot_of_gui_notice",
                "Append comment text with notice about sending screenshot of this gui tells nothing to modpack creators.",
                true);
        ArrayList<String> defaultBlacklistedLogs = new ArrayList<>();
        defaultBlacklistedLogs.add("CrashAssistant: latest.log");
        addOption("general.blacklisted_logs",
                "List of blacklisted log files. This files won't show in GUI logs list.",
                defaultBlacklistedLogs);

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

        config.setComment("modpack_modlist", "Settings of modlist feature.\n" +
                "Adds in generated msg block about which mods modpack user added/removed/updated.\n" +
                "Also you can see diff by running '/crash_assistant modlist diff' command.");
        addOption("modpack_modlist.enabled",
                "Enable feature.",
                true);
        addOption("modpack_modlist.modpack_creators",
                "nicknames of players, who considered as modpack creator.\n" +
                        "Only this players can overwrite modlist.json\n" +
                        "If this feature is enabled and this array is empty, will be appended with nickname of current player.",
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

        config.setComment("crash_command", "Settings of '/crash_assistant crash' command feature.");
        addOption("crash_command.enabled",
                "Enable feature.",
                true);
        addOption("crash_command.seconds",
                "To ensure the user really wants to crash the game, the command needs to be run again within this amount of seconds.\n" +
                        "Set to <= 0 to disable the confirmation.",
                10);

        addOption("greeting.shown_greeting",
                "You don't need to touch this option.\n" +
                        "On first world join of modpack creator if set to false shows greeting, then self enables.",
                false);


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


    public static void executeWithLock(Runnable function) {
        CONFIG_PATH.getParent().toFile().mkdirs();
        CONFIG_LOCK_PATH.toFile().getParentFile().mkdirs();
        try (var lockChannel = FileChannel.open(CONFIG_LOCK_PATH, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = lockChannel.lock()) {
            function.run();
            Files.deleteIfExists(CONFIG_LOCK_PATH);
        } catch (OverlappingFileLockException e) { // Current JVM FileLock already locked, ignoring
            function.run();
        } catch (IOException e) {
            throw new RuntimeException("Error accessing or locking the tmp file.", e);
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
        for (var entry : config.valueMap().entrySet()) {
            var value = entry.getValue();
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

    public static String get(String path, boolean applyPlaceHolders) {
        return applyPlaceHolders ? Lang.applyPlaceHolders(config.get(path), new HashSet<>()) : config.get(path);
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
