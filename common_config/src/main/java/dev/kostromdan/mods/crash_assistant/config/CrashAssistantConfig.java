package dev.kostromdan.mods.crash_assistant.config;

import com.electronwill.nightconfig.core.AbstractCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
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
import java.util.concurrent.atomic.AtomicReference;

public class CrashAssistantConfig {
    private static final Path CONFIG_PATH = Paths.get("config", "crash_assistant", "config.toml");
    private static final Path CONFIG_LOCK_PATH = Paths.get("local", "crash_assistant", "CONFIG_LOCK.tmp");
    private static final Logger LOGGER = LogManager.getLogger();

    private static CommentedFileConfig config;
    private static HashSet<String> usedOptions = new HashSet<>();
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
        addOption("general.help_link",
                "Link which will be opened in browser on request_help_button pressed.",
                "https://discord.gg/moddedmc");
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

        config.setComment("debug", "Here you can configure debug options for easier configuration of the mod.");
        addOption("debug.crash_game_on_event",
                "Setting this value to one of listed here, will crash the game in order to show/debug gui.\n" +
                        "NONE - default value, no crash. You can always crash game by holding vanilla F3+C keybind or '/crash_assistant crash' command\n" +
                        "MIXIN_SETUP - will crash game on Mixin setup. Crash report not generated.\n" +
                        "MOD_LOADING - will crash game on load of this mod. Can be used to show FML error screen. Crash report generated.\n" +
                        "GAME_STARTED - will crash game on first tick of TitleScreen. Crash report generated.",
                "NONE");

        config.setComment("text", "Here you can change text of lang placeHolders.\n" +
                "Also you can change any text in lang files.\n" +
                "You don't need to modify jar. You can change it in config/crash_assistant/lang. For more info read README.md file located where.");
        addOption("text.support_name",
                "$SUPPORT_NAME$ in lang files will be replaced with this value.\n" +
                        "For example this placeHolder used in: \"gui.request_help_button\": \"request help in $SUPPORT_NAME$\"",
                "Modded Minecraft Discord");
        addOption("text.support_place",
                "$SUPPORT_PLACE$ in lang files will be replaced with this value.",
                "#player_help channel");
        addOption("text.modpack_name",
                "$MODPACK_NAME$ in lang files will be replaced with this value.\n" +
                        "For example this placeHolder used in: \"gui.title_crashed_with_report\": \"Oops, $MODPACK_NAME$ crashed!\"\n" +
                        "Supports Better Compatibility Checker integration. You can use $BCC.modpackName$, $BCC.modpackVersion$, etc and it will be replaced with value from config/bcc-common.toml",
                "Minecraft");

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

        config.setComment("crash_command", "Settings of '/crash_assistant crash' command feature.");
        addOption("crash_command.enabled",
                "Enable feature.",
                true);
        addOption("crash_command.seconds",
                "To ensure the user really wants to crash the game, the command needs to be run again within this amount of seconds.\n" +
                        "Set to <= 0 to disable the confirmation.",
                10);


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
        });
        toRemove.forEach(key -> {
            config.remove(key);
            LOGGER.warn("Removed config option due to it not used in config anymore: " + key);
        });
    }

    private static <T> void addOption(String path, String comment, T defaultValue) {
        usedOptions.add(path);
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

    public static long getCommentsHash(){
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

    public static <T> T get(String path) {
        final AtomicReference<T> result = new AtomicReference<>();
        executeWithLock(() -> {
            update();
            result.set(config.get(path));
        });
        return result.get();
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
