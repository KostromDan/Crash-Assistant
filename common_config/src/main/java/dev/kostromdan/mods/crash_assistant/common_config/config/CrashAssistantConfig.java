package dev.kostromdan.mods.crash_assistant.common_config.config;

import com.electronwill.nightconfig.core.AbstractCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;
import dev.kostromdan.mods.crash_assistant.common_config.lang.Lang;
import org.apache.commons.jexl3.annotations.NoJexl;
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
    private static final long READ_CACHE_TTL_NANOS = 1_000_000_000L;
    private static long lastReadCacheUpdateNanos;
    private static volatile boolean readCacheValid;

    private static final LinkedHashSet<String> canonicalSectionOrder = new LinkedHashSet<>();
    private static final Map<String, LinkedHashSet<String>> canonicalKeysPerSection = new LinkedHashMap<>();

    static {
        executeWithLock(() -> {
            config = CommentedFileConfig.builder(CONFIG_PATH, TomlFormat.instance())
                    .preserveInsertionOrder()
                    .build();
            load();
            refreshReadCache();
        });
    }

    private static void setupDefaultValues() {
        usedOptions.clear();

        canonicalSectionOrder.clear();
        canonicalKeysPerSection.clear();

        config.setComment("general", "General settings of Crash Assistant mod.");
        if (Objects.equals(config.get("general.help_link"), "https://discord.gg/moddedmc")) {
            config.remove("general.help_link");
        }
        addOption("general.help_link",
                "Link which will be opened in browser on request_help_button pressed.\n" +
                        "If equals CHANGE_ME, will open Forge/NeoForge/Fabric/Quilt discord link. Names of communities/channels also will be used not from config, but according to this link.\n" +
                        "Must start with 'https://' or 'www.'",
                "CHANGE_ME");
        addOption("general.formulation_type",
                "With this option, you can select the formulation of the GUI (e.g., in the help buttons, title).\n" +
                        "Currently supported:\n" +
                        "   - DISCORD: Uses terminology like 'join the Discord' and 'post in #channel'.\n" +
                        "   - GITHUB: Uses terminology like 'visit the GitHub' and 'post into a new issue'.",
                "DISCORD");
        addOption("general.wrap_link",
                "Logs are always uploaded to mclo.gs. This option only controls which link is copied after the upload.\n" +
                        "This is needed to make viewing logs more convenient, especially on mobile.\n" +
                        "Supported values:\n" +
                        "   - mclo.gs: copies the original mclo.gs link.\n" +
                        "   - gnomebot.dev: wraps the mclo.gs link through gnomebot.dev.\n" +
                        "   - kostromdan.dev (recommended): wraps the mclo.gs link through paste.kostromdan.dev and copies the short p.kdan.dev link.",
                "kostromdan.dev");
        addOption("general.send_uploaded_logs_data_to_kostromdan_dev",
                "If enabled, Crash Assistant sends metadata about uploaded logs to api.kostromdan.dev after upload.\n" +
                        "This does not upload log contents to our server: logs are still uploaded to mclo.gs.\n" +
                        "This metadata is needed so paste.kostromdan.dev can switch between logs uploaded from the same crash in the browser,\n" +
                        "and can check/show the Minecraft UUID (for piracy check purposes) for those logs even if a particular uploaded log does not contain it.\n" +
                        "This works independently from general.wrap_link.",
                true);
        addOption("general.show_on_fml_error_screen",
                "Show gui on minecraft crashed on modloading and FML error screen displayed.",
                true);
        addOption("general.kill_old_app",
                "Close old CrashAssistantApp if it's still running when starting a new instance of Minecraft, to avoid confusing player with window from old crash.",
                true);
        addOption("general.language_source",
                "Controls where the standalone Crash Assistant app gets the language for its interface.\n" +
                        "When this code runs inside the Minecraft process, the language is always read from Minecraft options.txt and this option is ignored.\n" +
                        "Supported values:\n" +
                        "   - SYSTEM (default): Uses the operating system language. Regional variants are matched to an available translation.\n" +
                        "     For example, en_US, en_GB, and other English locales use en_us when it is the available English translation.\n" +
                        "   - GAME: Uses the language from the lang entry in the Minecraft options.txt file.\n" +
                        "   - A language key such as en_us or ru_ru: Forces that language and ignores both the system and Minecraft languages.\n" +
                        "If the selected source cannot provide an available language, general.default_lang is used.",
                "SYSTEM");
        addOption("general.default_lang",
                "Fallback interface language used when general.language_source cannot provide an available language.\n" +
                        "Use a language key such as en_us or ru_ru.\n" +
                        "If this language is also unavailable, Crash Assistant falls back to en_us.",
                "en_us");
        addOption("general.priority_lang_for_overrides",
                "By default, crash assistant will get the value for the current language from the overrides folder, then from the jar.\n" +
                        "By changing this option, it will first try to get it from the current overrides, then (if no override exists for this language)\n" +
                        "from the override for the language specified here, and only then from the jar language.\n" +
                        "Use \"NONE\" to disable this feature. Or language key, like \"en_us\" to enable it.",
                "NONE");
        addOption("general.generate_localization_overrides_folder_with_readme",
                "By changing this value you can disable creating \"crash_assistant_localization_overrides\" folder and placing \"README.md\" file there.",
                true);
        addOption("general.prevent_upload_buttons_delay",
                "By default our process is scanning for terminated processes(they can appear with delay) and after finish of scan enabling upload buttons.\n" +
                        "This option can prevent this and enable individual upload buttons immediately after crash.\n" +
                        "However can confuse users and make them clicking individual upload buttons instead of Upload All Button.",
                false);
        addOption("general.enable_mclogs_anti_ip_like_version_censorer",
                "Unfortunately mclogs censoring ip-like versions as has no way to determine if it's version or IP.\n" +
                        "This will prevent this by replacing dot's in them to dot-like symbol.\n" +
                        "Anti censoring only versions, IP's are kept censored.",
                true);
        addOption("general.prevent_generating_crash_assistant_app_logs",
                "Prevents creating \"crash_assistant\" folder in the logs folder.\n" +
                        "So this option prevents our app logging at all.\n" +
                        "HIGHLY UNRECOMMENDED to disable! Contains many useful info.",
                false);
        addOption("general.generate_own_launcher_log",
                "Generates \"logs/stderr_stream.log\" with stderr stream.\n" +
                        "Since many launchers are not saving this info, which is extremely helpful for debugging some crashes.\n" +
                        "As only where crash reason is present. Keeps original stream untouched, just logs it to a file.",
                true);
        addOption("general.win_event_is_crash",
                "If true, Crash Assistant will pop up even if the presence of win_event log is the only sign of a crash.\n" +
                        "Can be false positive for some users, since they can have some unrelated spam in the windows event error log.",
                false);
        addOption("general.logs_priority_overrides",
                "Here you can change priority for logs.\n" +
                        "For example if you want crash report to be shown earlier than latest.log in the available logs list.\n" +
                        "Supported values: https://github.com/KostromDan/Crash-Assistant/blob/1.19-1.20.1/app/src/main/java/dev/kostromdan/mods/crash_assistant/app/logs_analyser/LogType.java\n" +
                        "Usage: [\"CRASH_REPORT\", \"LOG\"]",
                new ArrayList<>());
        ArrayList<String> defaultBlacklistedLogs = new ArrayList<>();
        addOption("general.blacklisted_logs",
                "List of blacklisted log files (checked with startswith()). This files won't show in GUI logs list.",
                defaultBlacklistedLogs);
        List<String> blacklistedLogs = config.get("general.blacklisted_logs");
        if (blacklistedLogs.contains("CrashAssistant: latest.log")) {
            blacklistedLogs.remove("CrashAssistant: latest.log");
            config.set("general.blacklisted_logs", blacklistedLogs);
        }

        config.setComment("simple_mode", "A simplified GUI that hides the logs list until the user opts into Expert Mode.");
        addOption("simple_mode.enabled",
                "If enabled, the GUI starts in simple mode with logs hidden and a single \"Show Logs (Expert Mode)\" button.\n" +
                        "Disabled by default.",
                false);
        addOption("simple_mode.prevent_for_modpack_creators",
                "If true, modpack creators always see the Expert Mode with logs visible, even if simple mode is enabled.",
                true);
        addOption("simple_mode.hide_modlist_section",
                "If true, hides the mod list changes section while simple mode is active.",
                false);

        config.setComment("text", "Here you can change text of lang placeHolders.\n" +
                "Also you can change any text in lang files.\n" +
                "You don't need to modify jar. You can change it in config/crash_assistant/lang. For more info read README.md file located where.");
        if (Objects.equals(config.get("text.support_name"), "Modded Minecraft Discord") || Objects.equals(config.get("text.support_name"), "mod loader Discord")) {
            config.remove("text.support_name");
        }
        addOption("text.support_name",
                "$CONFIG.text.support_name$ in lang files will be replaced with this value.\n" +
                        "For example this placeHolder used in: \"Request help in the $CONFIG.text.support_name$\"\n" +
                        "Recommended values: \"Modpack-X Discord\", \"Modpack-X GitHub repository\", etc.",
                "example Discord");
        addOption("text.support_place",
                "$CONFIG.text.support_place$ in lang files will be replaced with this value.\n" +
                        "Recommended values: \"#support channel\".\n" +
                        "In case of formulation type `GITHUB` won't be used.",
                "#example channel");
        addOption("text.modpack_name",
                "$CONFIG.text.modpack_name$ in lang files will be replaced with this value.\n" +
                        "For example this placeHolder used in: \"Oops, $CONFIG.text.modpack_name$ crashed!\"\n" +
                        "Supports Better Compatibility Checker integration. You can use $BCC.modpackName$, $BCC.modpackVersion$, etc and it will be replaced with value from BCC config.",
                "Minecraft");

        config.setComment("generated_message", "Settings of message generated by Upload all button");
        addOption("generated_message.prefix",
                "Add prefix before filename.\n" +
                        "This can prevent too small, hard to hit on mobile links.\n" +
                        "Recommended values: \"### \".",
                "### ");
        addOption("generated_message.logs_separator",
                "Separator between logs.\n" +
                        "If you want vertical logs, use \"\\n$PREFIX$\".",
                "   |   ");
        addOption("generated_message.intel_corrupted_notification",
                "Adds line in log list about this Intel processor can be corrupted.",
                true);
        addOption("generated_message.piracy_notification",
                "Adds line in log list about piracy/offline mode or if check failed.",
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
                        "Can be needed to be disabled if issues are reported to something not supporting ANSI codeblocks, like GitHub.\n" +
                        "If you disable this option, you may also want to remove \"ansi\" from \"generated_message.ansi_block_pattern\".",
                true);

        addOption("generated_message.message_structure",
                "Structure of the generated message.\n" +
                        "Variables:\n" +
                        "$HEADER$ - Title and upload link\n" +
                        "$TEXT_UNDER_CRASHED$ - Custom text from config\n" +
                        "$PREFIX$ - The prefix string\n" +
                        "$LOGS$ - The joined logs\n" +
                        "$PROBLEMATIC_FRAME$ - hs_err frame\n" +
                        "$ANALYSIS_RESULT$ - Analysis results\n" +
                        "$MODLIST_DIFF$ - Modlist changes\n" +
                        "$CUSTOM/name$ - Entries added by scripts to the named custom slot",
                "$HEADER$$TEXT_UNDER_CRASHED$$PREFIX$$LOGS$\n$PROBLEMATIC_FRAME$$ANALYSIS_RESULT$$MODLIST_DIFF$");

        addOption("generated_message.log_line_pattern",
                "Pattern for a single log line.\n" +
                        "Variables: $LOG_NAME$, $FILE_NAME$, $LINK$.",
                "$LOG_NAME$[$FILE_NAME$](<$LINK$>)");

        addOption("generated_message.log_line_split_pattern",
                "Pattern for a split log line (too large for single upload).\n" +
                        "Variables: $LOG_NAME$, $FILE_NAME$, $LINK_FIRST_LINES$, $LINK_LAST_LINES$, $TOO_BIG_REASONS$.",
                "$LOG_NAME$[$FILE_NAME$ <TOLOWER>$MSG_LANG.gui.split_log_dialog_head$</TOLOWER>](<$LINK_FIRST_LINES$>) / [<TOLOWER>$MSG_LANG.gui.split_log_dialog_tail$</TOLOWER>](<$LINK_LAST_LINES$>) $TOO_BIG_REASONS$");

        addOption("generated_message.ansi_block_pattern",
                "Pattern for ModList Diff and Analysis Results blocks.\n" +
                        "Variables: $PREFIX$, $HEADER$, $CONTENT$.\n" +
                        "If 'color_message' is false, remove 'ansi'.",
                "$PREFIX$$HEADER$\n```ansi\n$CONTENT$\n```");

        addOption("generated_message.problematic_frame_pattern",
                "Pattern for formatting the problematic frame from hs_err.\n" +
                        "Variables: $CONTENT$",
                "```java\n$CONTENT$\n```");

        addOption("generated_message.link_notification_pattern",
                "Pattern for simple link notifications (e.g. Intel fix, Piracy check, KubeJS scripts).\n" +
                        "Variables: $TEXT$, $LINK$",
                "[$TEXT$](<$LINK$>)");

        addOption("generated_message.modlist_header_pattern",
                "Pattern for the header of the modlist diff block (containing the link).\n" +
                        "Variables: $PART1$ (text before link), $PART2$ (text after link), $LINK$.",
                "[$PART1$](<$LINK$>)$PART2$");

        config.setComment("copied_links", "Settings of links copied by Upload and copy link buttons");
        addOption("copied_links.single_link",
                "With this option, you can customize how single links from individual upload buttons are copied, there\n" +
                        "log of small size was uploaded to a single link.\n" +
                        "For example, leave just $LINK$ to copy just link.",
                "$LOG_NAME$$FILE_NAME$: $LINK$");
        addOption("copied_links.single_link_split",
                "With this option, you can customize how links from individual upload buttons are copied, there\n" +
                        "log was split to the 2 parts (head and tail, due to too large size for single upload), but user only decided to copy only one of them (not message with both).",
                "$LOG_NAME$$FILE_NAME$($HEAD_OR_TAIL$): $LINK$ $TOO_BIG_REASONS$");
        addOption("copied_links.both_links_split",
                "With this option, you can customize how links from individual upload buttons are copied, there\n" +
                        "log was split to the 2 parts (head and tail, due to too large size for single upload), but user decided to copy message with both.",
                "$LOG_NAME$[$FILE_NAME$ <TOLOWER>$MSG_LANG.gui.split_log_dialog_head$</TOLOWER>](<$LINK_FIRST_LINES$>) / [<TOLOWER>$MSG_LANG.gui.split_log_dialog_tail$</TOLOWER>](<$LINK_LAST_LINES$>) $TOO_BIG_REASONS$");
        addOption("copied_links.skip_split_dialog",
                "If enabled, disables the head/tail selection dialog for split logs on individual uploads and always copies message with both links.",
                false);

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
        addOption("modpack_modlist.add_datapacks",
                "If enabled, will add datapacks to modlist.json\n" +
                        "After filename where will be ' (datapacks)' suffix.",
                false);
        addOption("modpack_modlist.add_modloader_jar_name",
                "If enabled, will add modloader jar name to modlist, to easily track if user changed version of modloader.",
                true);
        addOption("modpack_modlist.add_modlist_txt_as_log",
                "If enabled, will add generated modlist.txt, with names of all mods / modids / mixin configs / jarjar mods info to logs.",
                true);

        config.setComment("too_many_changes_warning",
                "Settings of too many changes warning feature.\n" +
                        "Notifies end users of the modpack and saying they made too many changes to the modpack.\n" +
                        "Not displayed to the modpack creators.");
        addOption("too_many_changes_warning.count",
                "Set to the positive integer to enable feature. Set to negative integer to disable.\n" +
                        "How many changes end user should make for warning to be displayed.",
                -1);
        addOption("too_many_changes_warning.formulation_type",
                "With this option, you can select the formulation of this warning, currently supported:\n" +
                        "   - NOTIFY: Just saying to the end user that what they made many changes and adding random mods or clicking\n" +
                        "the \"Update All\" button is not a good idea without proper testing. It is expected to crash.\n" +
                        "   - DROP_SUPPORT: Saying what you are not providing support for that amount of changes, suggesting the end user to\n" +
                        "re-install modpack or they are on their own with that amount of changes.",
                "NOTIFY");

        config.setComment("analysis", "Settings of analysis feature.\n" +
                "Analysing logs for most common reasons of crashes and displaying recommendations with fixes.");
        addOption("analysis.enabled",
                "Enable feature.",
                true);
        addOption("analysis.trigger_on_original_modpack_mods",
                "By default if CA work in modpack mode and ran by the end user it won't display analysis if it blames mod which was part of original modpack.\n" +
                        "And will blame only mods which were added/updated by end users. By enabling this option you can bypass this and it will blame any mods.",
                false);
        addOption("analysis.first_show_delay",
                "Delay in seconds for the OK button when a crash reason is shown for the first time.\n" +
                        "Helps to ensure the user reads the recommendation.\n" +
                        "Set to 0 or -1 to disable the delay.",
                10);
        addOption("analysis.blacklisted_reasons",
                "Here you can disable some Analysis by class names.\n" +
                        "List of them can be found here: dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons\n" +
                        "For example \"Create6Addons\"",
                new ArrayList<>());

        config.setComment("analysis_tools", "Settings of analysis tools feature.\n" +
                "Here you can enable or disable showing some analysis tools for end user.");
        addOption("analysis_tools.enabled",
                "Enable feature.",
                true);
        addOption("analysis_tools.blacklisted_tools",
                "Here you can disable some Analysis tools by class names.\n" +
                        "List of them can be found here: dev.kostromdan.mods.crash_assistant.app.gui.analysis\n" +
                        "For example \"MCreatorModDetectorGUI\"",
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

        config.setComment("piracy", "Settings of notifying about piracy/offline mode.");
        addOption("piracy.enabled",
                "Enable feature.\n" +
                        "Note: This option is ignored if general.help_link is default (CHANGE_ME).\n" +
                        "Notifies if in your community (Discord/GitHub) piracy is not supported. Recommended to enable for modpacks.",
                false);
        addOption("piracy.delay",
                "Delay in seconds for the OK button when piracy warning is shown.\n" +
                        "Set to 0 or -1 to disable the delay.",
                10);

        addOption("greeting.shown_greeting",
                "You don't need to touch this option.\n" +
                        "On first world join of modpack creator if set to false shows greeting, then self enables.",
                false);

        config.setComment("scripts", "Settings of scripting feature.\n" +
                "Allows you to add custom scripts for log analysis.\n" +
                "Scripts should be placed in config/crash_assistant/scripts/log_analysis folder.");
        addOption("scripts.enabled",
                "Enable feature.",
                true);
        addOption("scripts.ide_enabled",
                "Enable Scripts IDE in the GUI.",
                true);
        addOption("scripts.generate_scripts_folder_with_example",
                "By changing this value you can disable creating \"scripts\" folder and placing example scripts there.",
                true);
        addOption("scripts.attach_startup_scripts_log_only_on_error",
                "If enabled, \"logs/crash_assistant/startup_scripts.log\" is added to the logs list only when it contains [ERROR].\n" +
                        "If it has no errors, its contents are written into \"crash_assistant_app.log\" instead.\n" +
                        "If app logging is disabled, the startup scripts log is kept as a separate log file.",
                true);

        config.setComment("gui_customisation", "You can customise GUI with this options.");
        addOption("gui_customisation.theme_file_name",
                "Name of a FlatLaf IntelliJ Themes file in config/crash_assistant folder or core theme name.\n" +
                        "If file doesn't exist and no core theme matches, themes will be disabled and Standard Swing look and feel used.\n" +
                        "Supported core themes: 'FlatLightLaf', 'FlatDarkLaf', 'FlatIntelliJLaf', 'FlatDarculaLaf', 'FlatMacLightLaf', 'FlatMacDarkLaf'.\n" +
                        "Supports any IntelliJ Theme file with '.theme.json' extension.\n" +
                        "If you prefer standard Swing look and feel, use 'Swing'.\n" +
                        "Look out our guide about theme support: https://github.com/KostromDan/Crash-Assistant/blob/pages/guides/Theme%20Support/Getting%20Started.md",
                "FlatLightLaf");
        addOption("gui_customisation.disable_upload_all_button",
                "Will hide Upload All Button from GUI.",
                false);
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
        addOption("gui_customisation.simple_mode_button_font_size",
                "Same as upload_all_button_font_size, but for the Simple Mode toggle button.",
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
        addOption("gui_customisation.simple_mode_button_foreground_color",
                "Same as upload_all_button_foreground_color, but for the Simple Mode toggle button.",
                "0_0_178");
        addOption("gui_customisation.auto_fix_button_font_size",
                "Same as upload_all_button_font_size, but for Auto-Fix button (in integrated GPU warning).",
                16);
        addOption("gui_customisation.auto_fix_button_foreground_color",
                "Same as upload_all_button_foreground_color, but for Auto-Fix button (in integrated GPU warning).\n" +
                        "Default for this button is \"0_178_0\" (dark green color).",
                "0_178_0");
        addOption("gui_customisation.privacy_policy_yes_button_foreground_color",
                "Same as upload_all_button_foreground_color, but for the Yes button in the privacy policy acceptance dialog.\n" +
                        "Default for this button is \"0_178_0\" (dark green color).",
                "0_178_0");
        addOption("gui_customisation.privacy_policy_no_button_foreground_color",
                "Same as upload_all_button_foreground_color, but for the No button in the privacy policy acceptance dialog.\n" +
                        "Default for this button is \"255_0_0\" (red color).",
                "255_0_0");
        addOption("gui_customisation.screenshot_of_gui_notice_animated_border_color",
                "Color of the animated border for the screenshot notice.\n" +
                        "format is \"R_G_B\", range is 0-255, for example \"255_0_0\" is red color.\n" +
                        "Default for this is \"255_0_0\" (red color).",
                "255_0_0");
        addOption("gui_customisation.screenshot_of_gui_notice_text_color",
                "Color of the text for the screenshot notice (inside the animated border).\n" +
                        "format is \"R_G_B\", range is 0-255, for example \"255_0_0\" is red color.\n" +
                        "Default for this is \"255_0_0\" (red color).",
                "255_0_0");
        addOption("gui_customisation.blinking_button_success_color",
                "Color of the button blinking on success action (e.g. upload all finished).\n" +
                        "format is \"R_G_B\", range is 0-255.\n" +
                        "Default is \"100_255_100\" (light green).",
                "100_255_100");
        addOption("gui_customisation.blinking_button_attention_color",
                "Color of the button blinking on attention request (e.g. click on link in description).\n" +
                        "format is \"R_G_B\", range is 0-255.\n" +
                        "Default is \"100_100_255\" (light blue).",
                "100_100_255");
        addOption("gui_customisation.blinking_button_error_color",
                "Color of the button blinking on error (e.g. failed upload).\n" +
                        "format is \"R_G_B\", range is 0-255.\n" +
                        "Default is \"255_100_100\" (light red).",
                "255_100_100");
        addOption("gui_customisation.modpack_logo_path",
                "Path to a modpack logo to display in the top of the GUI.\n" +
                        "Path is relative to the Minecraft instance folder. Leave empty to disable.\n" +
                        "WARNING: use only '/' path separator. '\\' will corrupt config!",
                "");
        addOption("gui_customisation.modpack_logo_large_mode",
                "If true, the logo will be larger, Replacing a some of `don't send screenshot` notice.\n" +
                        "If false, it will be smaller and logo will be end right where the `don't send screenshot` notice starts.\n" +
                        "You should try both, but most likely:\n" +
                        "- If you have some long text in the discord/github name, you will love the small one.\n" +
                        "- If the text is short, you will love the large one.",
                false);
        addOption("gui_customisation.limit_modpack_logo_height",
                "Limit modpack logo height. Default is -1, which means it's calculated automatically.\n" +
                        "By default, this should not be needed. But if you have heavily customized GUI or using\n" +
                        "a rectangle logo instead of square, you may want to decrease its size, so this option could be needed in such case. ",
                -1);
        addOption("gui_customisation.modpack_logo_aligned_center",
                "This option would be needed only if you limited modpack logo height.\n" +
                        "Otherwise, the logo will consume all available horizontal space.\n" +
                        "If true, the logo will be centered. If false, it will be aligned to the top. ",
                true);

        config.setComment("compatibility", "Checks crash_assistant compatibility with other incompatible mods.\n" +
                "Highly unrecommended to disable!");
        addOption("compatibility.enabled",
                "Enable feature.",
                true);

        config.setComment("debug", "Options to help debug the Crash Assistant.");
        addOption("debug.crash_after_init",
                "Debug option to crash immediately after Crash Assistant launched its process to conveniently debug it, configure it without need to manually crash.",
                false);

        config.setComment("patches", "Options to configure patches made by Crash Assistant.");
        addOption("patches.MC307905",
                "Fixes vanilla crash MC-307905 during crash report generation when StackTraceElement#getFileName() returns null.\n" +
                        "Without this fix, crash report generation itself crashes under some rare conditions, preventing the original crash report from being created.\n" +
                        "This patch does not change the report contents; it only allows the crash report to be generated successfully.",
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

        // Record canonical order (section + immediate child key)
        String[] parts = path.split("\\.", 2);
        String section = parts[0];
        canonicalSectionOrder.add(section);
        if (parts.length > 1) {
            String sub = parts[1];
            int dot = sub.indexOf('.');
            if (dot >= 0) sub = sub.substring(0, dot);
            canonicalKeysPerSection
                    .computeIfAbsent(section, s -> new LinkedHashSet<>())
                    .add(sub);
        }

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

    public static ArrayList<String> getPriorityOverridesForLogsOrder() {
        return get("general.logs_priority_overrides");
    }

    public static ArrayList<String> getBlacklistedAnalysis() {
        return get("analysis.blacklisted_reasons");
    }

    public static ArrayList<String> getBlacklistedAnalysisTools() {
        return get("analysis_tools.blacklisted_tools");
    }

    public static ArrayList<String> getModpackCreators() {
        return get("modpack_modlist.modpack_creators");
    }

    public static void addModpackCreator(String nickname) {
        ArrayList<String> currentModpackCreators = getModpackCreators();
        currentModpackCreators.add(nickname);
        set("modpack_modlist.modpack_creators", currentModpackCreators);
    }

    @NoJexl
    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    @NoJexl
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

    private static void update() {
        executeWithLock(() -> {
            if (!CONFIG_PATH.toFile().exists() || CONFIG_PATH.toFile().lastModified() > lastConfigUpdate) {
                load();
            }
        });
    }

    private static void load() {
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
            long old_order_hash = getOrderHash();

            setupDefaultValues(); // fills canonical order and adds/removes keys

            // Detect misalignment and restore canonical order if needed
            if (!isCanonicalOrderAligned()) {
                enforceCanonicalOrder();
            }

            if (config.valueMap().hashCode() != old_values_hash || getCommentsHash() != old_comments_hash || getOrderHash() != old_order_hash) {
                save();
            }
            lastConfigUpdate = CONFIG_PATH.toFile().lastModified();
        });
    }

    private static long getCommentsHash() {
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

    private static long getOrderHash() {
        List<String> tokens = new ArrayList<>();
        for (Map.Entry<String, Object> e : config.valueMap().entrySet()) {
            String top = e.getKey();
            tokens.add("#" + top);
            Object v = e.getValue();
            if (v instanceof AbstractCommentedConfig) {
                AbstractCommentedConfig sec = (AbstractCommentedConfig) v;
                for (String k : sec.valueMap().keySet()) {
                    tokens.add(top + "." + k);
                }
            }
        }
        return tokens.hashCode();
    }

    private static boolean isCanonicalOrderAligned() {
        // Root sections: compare current order filtered to canonical vs canonical filtered to present
        List<String> currentRoot = new ArrayList<>();
        for (Map.Entry<String, Object> e : config.valueMap().entrySet()) {
            if (e.getValue() instanceof AbstractCommentedConfig) {
                currentRoot.add(e.getKey());
            }
        }
        List<String> currentRootCanonOnly = new ArrayList<>();
        for (String s : currentRoot) if (canonicalSectionOrder.contains(s)) currentRootCanonOnly.add(s);

        List<String> canonicalRootPresent = new ArrayList<>();
        for (String s : canonicalSectionOrder) if (currentRoot.contains(s)) canonicalRootPresent.add(s);

        if (!currentRootCanonOnly.equals(canonicalRootPresent)) return false;

        // Per-section keys
        for (String section : canonicalRootPresent) {
            Object v = config.get(section);
            if (!(v instanceof AbstractCommentedConfig)) continue;
            AbstractCommentedConfig sec = (AbstractCommentedConfig) v;

            List<String> now = new ArrayList<>(sec.valueMap().keySet());
            LinkedHashSet<String> canonSet = canonicalKeysPerSection.getOrDefault(section, new LinkedHashSet<>());

            List<String> nowCanonOnly = new ArrayList<>();
            for (String k : now) if (canonSet.contains(k)) nowCanonOnly.add(k);

            List<String> canonPresent = new ArrayList<>();
            for (String k : canonSet) if (now.contains(k)) canonPresent.add(k);

            if (!nowCanonOnly.equals(canonPresent)) return false;
        }
        return true;
    }

    private static void enforceCanonicalOrder() {
        // Reorder root sections to canonical (present-only)
        List<String> desiredRoot = new ArrayList<>();
        for (String s : canonicalSectionOrder) {
            if (config.valueMap().containsKey(s)) desiredRoot.add(s);
        }
        reorderMap(config.valueMap(), desiredRoot);

        // Reorder each section's keys to canonical (present-only)
        for (String section : desiredRoot) {
            Object v = config.get(section);
            if (!(v instanceof AbstractCommentedConfig)) continue;
            AbstractCommentedConfig sec = (AbstractCommentedConfig) v;

            LinkedHashSet<String> canon = canonicalKeysPerSection.getOrDefault(section, new LinkedHashSet<>());
            List<String> desiredKeys = new ArrayList<>();
            for (String k : canon) if (sec.valueMap().containsKey(k)) desiredKeys.add(k);
            reorderMap(sec.valueMap(), desiredKeys);
        }
    }

    private static void reorderMap(Map<String, Object> map, List<String> desiredOrder) {
        // Rebuild map in the desired order; append any leftovers at the end (shouldn't be any after cleanup)
        LinkedHashMap<String, Object> old = new LinkedHashMap<>(map);
        map.clear();
        for (String k : desiredOrder) {
            if (old.containsKey(k)) {
                map.put(k, old.remove(k));
            }
        }
        // append leftovers if any (safety)
        for (Map.Entry<String, Object> e : old.entrySet()) {
            map.put(e.getKey(), e.getValue());
        }
    }

    private static void save() {
        executeWithLock(() -> {
            config.save();
            lastConfigUpdate = CONFIG_PATH.toFile().lastModified();
            readCacheValid = false;
        });
    }

    public static synchronized <T> T get(String path) {
        long elapsedSinceCacheUpdate = System.nanoTime() - lastReadCacheUpdateNanos;
        if (readCacheValid && elapsedSinceCacheUpdate >= 0 && elapsedSinceCacheUpdate < READ_CACHE_TTL_NANOS) {
            return config.get(path);
        }

        final AtomicReference<T> result = new AtomicReference<>();
        executeWithLock(() -> {
            update();
            result.set(config.get(path));
            refreshReadCache();
        });
        return result.get();
    }

    private static void refreshReadCache() {
        lastReadCacheUpdateNanos = System.nanoTime();
        readCacheValid = true;
    }

    public static boolean getBoolean(String path) {
        return get(path);
    }

    public static int getInteger(String path) {
        return get(path);
    }

    public static String get(String path, boolean applyPlaceHolders) {
        String value = get(path);
        return applyPlaceHolders ? Lang.applyPlaceHolders(value, new HashMap<>()) : value;
    }

    public static <T> void set(String path, T value) {
        readCacheValid = false;
        try {
            executeWithLock(() -> {
                update();
                config.set(path, value);
                save();
            });
        } finally {
            synchronized (CrashAssistantConfig.class) {
                readCacheValid = false;
            }
        }
    }

    @NoJexl
    public static void main(String[] args) { // Debug config.
    }
}
