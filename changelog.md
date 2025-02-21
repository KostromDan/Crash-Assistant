1.4.0:
- Load Intel bug GIF asynchronously from github to reduce mod size.
- Improved Intel bug message.
- Introducing Logs Analyser. Analysing logs dor most common crash reasons and displaying message with fix.
  Currently implemented 2 checks:
  - Problematic frame atio6axx.dll
  - User done some input on loading, which caused freeze and taskkill.
- Very many small fixes.
- License MIT -> LGPL. LGPL feels closer to me than MIT. If you are not familiar with it, main change is:
  - MIT: "Take my mod and do whatever you want, just mention me."
  - LGPL: "Use my mod however you like, but if you change it, share the changes licensed under LGPL."

1.3.9:
- Parsing mod data async. To prevent GUI starting too long if mod list is too big.
- Added logging of time for parsing mod data.
- Optimised ModDataParser.
- Fixed some potential issues.
- Moved MCLogs init from startup to then needed. Since it took a lot of time. To improve startup speed.
- Fix error msg in dev env.
- Improved formulations of Intel bug warn.

1.3.8:
- Localisation to Intel processors warn.
- Added notification about Intel processors in generated msg.
- Don't show again checkbox for Intel processors warning.
- Improved window size calculation of Intel processors warning.

1.3.7:
- Implemented affected processors check by rather simpler way. This fixed all the issues.

1.3.6:
- Fixed jna wasn't blacklisted from fatjar and wasn't passed to app.
- Fixed oshi wasn't loaded properly. So Intel check wasn't worked.

1.3.5:
- Update zh_cn lang.
- Renamed latest.log to crash_assistant_app.log to prevent confusion.
- Removed crash_assistant_app.log from blacklisted by default logs, since it can contain useful information.
- Added `crashed without a crash report.` to generated msg.
- Added `generated_message.one_line_logs` config option.
  - Replaces "\n" separator between logs to "   |   " to make message vertically smaller. Enabled by default.
- Added waning screen about processors affected by critical Intel Chip Bug.
  - Config option `intel_corrupted.enabled`
  - Config option `intel_corrupted.show_gif`. Show funny related gif in warning message.
- Very many small fixes.

1.3.4:
- A little improved Event Viewer guide.

1.3.3:
- Added locating of terminated by Windows processes.
- Now Crash Assistant starts as independent process instead of child process.
Since all child processes being terminated on parent process termination by Windows.
- Removed ChildProcessLoggers.

1.3.2:
- Complete redesign of generated msg:
  - Now Discord msg colored.
  - Added config option enabled by default `generated_message.h3_prefix`: will make logs links in msg bigger, to be pleasant for mobile users.
  - If diff is too big and uploaded, will print colored count of each added/removed/updated mods.
- Fixed potential issues.

1.3.1:
- Fixed if mods were only updated, mod list diff msg was empty.
- Removed prefix if launcherlog filename already includes launcher name.

1.3.0:
- Added Updated mods block. Now updated mods will be displayed in independent block with just version diff.
- Removed `text.comment_start_formulation` config option.
  - Now if `help_link` equals `CHANGE_ME`, will be used `CANT_RESOLVE`, else `PLS_REPORT`.
- If diff is longer 15 lines, it will be uploaded. (3 for forge discord).
- Fixed on IOS Discord links were unclickable.
- Added mods.toml to forge coremod, to be compatible with 3-rd party services needing it.

1.2.20:
- If link is `CHANGE_ME`, `gnomebot.dev` for `general.upload_to` will be used.
- Improved config descriptions.
- Fixed I cherry-picked one commit by mistake from NeoForge, so we directed Forge users to NeoForge discord.

1.2.19:
- Added `modpack_modlist.add_resourcepacks` config option.

1.2.18:
- Link description in generated message surrounded by `` for better looking.
- Universal formulations of default discord names, channels was replaced to actual values.
- Added quilt discord.
- Significantly improved generated message formatting.
- Removed `generated_message.generated_msg_includes_info_why_split` config option.
 
1.2.17:
- Fixed app process won't finish / start GUI if Minecraft PID was reused immediately by another process. Mostly Linux problem. Maybe super rare Windows, but I haven't seen a single case.
- Added PolyMC launcherlog support.
- Added logging for GUI start time.
- Removed `debug.crash_game_on_event`, since we have crash command.
- Moved `shown_greeting` config option from `debug` to `greeting`. Don't be afraid if you receive it again.
- Added confirmation if help_link domain is not in trusted domains list.
- Added link to tooltip of request_help_button.
- Add extension `.jar` check then checking duplicated coremods.
- Reduce limit, then modlist diff uploaded to 1650, to give user 350 chars for feedback.
- Made universal formulation for mod list diff to fit both head formulations.
- Show upload all button warning after uploading (was before).
- Improved logger of app.
- Stop child process loggers after 3 seconds if app and not started successfully, and not crashed. Should never happen.
- Added `text.comment_start_formulation` config option.
- Improved general.help_link comment.
- Change info to warn if log file is empty.
- Replaced BetterMC discord link to mod loader Discord link.
- Small fixes.

1.2.16:
- Moved CrashAssistantApp logs from `local/crash_assistant/logs/` to `logs/crash_assistant`. Old folder will be deleted.
- Added CrashAssistantApp output logging to Minecraft log during start of CrashAssistantApp.
- Small fixes.

1.2.15:
- Fixed `gnomebot.dev` setting wasn't applied to mod list diff.
- Added setting for modpacks created for non-English-speaking audience to generate message on their language.
- Fixed some grammar in eng lang.
- Small fixes.

1.2.14:
- Fixed gui not starting on Apple.
- Replaced gnome.bot -> gnomebot.dev in strings. Idk from where I took first value.
- Added support of crafttweaker and rei logs.
- Improved logs adding.

1.2.14alpha:
- Attempt to fix gui not starting on Apple.

1.2.13:
- Removed ModdedMC Discord waning since it ignored by most of the users.
- Fixed stuck on Upload All Button press if available logs are empty.
- Added in generated message information about `CurseForge: skip launcher` used.
- Added new config option `general.upload_to`: supports `mclo.gs` / `gnomebot.dev`
    - Logs still always uploaded to mclo.gs, but setting to `gnomebot.dev` will open log where.
- Improved config.
- Added greeting msg with suggestion to configure mod, shown one time for modpack creator, who installed the mod.
- Small fixes.

1.2.12:
- Added checking duplicated mod. Since forge doesn't do this for coremods.
- Fixed alphabet sorting of mod list from prev update was CASE_SENSITIVE.
- Improved placeholders logic.
- Added `generated_message.text_under_crashed` config option.
- Added `generated_message.warning_after_upload_all_button_press` config option.
- Moved `generated_msg_includes_info_why_split` config option from `general` to `generated_message`. Option in `general` will void.
- Mod list diff will save only `.jar` and `.zip` extensions.
- Added another launcher launcherlog support.
- Removed some useless code.
- Fixed rare bug with buttons sizes.
- Changed highlight color from red to blue, so it still requests attention, but looks less aggressive.
- Upload all button after BetterMC Discord warning starts uploading immediately, without second press.

1.2.11:
- Simplified & improved Split logs dialog formulations.
- Improved ModdedMC Discord warning.
- Highlight available logs label after ModdedMC Discord warning closed.

1.2.10:
- Too big logs, which exceeding mclo.gs limits will be split into 2 parts: first and last lines containing 25k lines or 10MB.
- Significantly improved generated message formatting.
- Major log reading performance improvement. No more stuck on uploading even supermassive logs(tested on 10GB logs).
- modlist.json now is sorted by alphabet.
- Increased xmx to 512mb to prevent potential issues (No impact on RAM consumption on awaiting crash stage).
- Improved some formulations in config comments.
- Improved lang placeholders applying logic & performance.

1.2.7:
- Reduced memory usage of app for ~3mb.
- Add support of another launcher launcherlogs:
    - FTB Electron App
    - Prism Launcher
    - GDLauncher
    - MultiMC
    - Modrinth
- If generatedMsg + modlistDiff is larger than 2000 chars, modlistDiff will be uploaded to mclo.gs to fit non Nitro Discord limits.
- Fixed config comments hash wasn't updated properly.
- BCC config integration. Now you can use values from it as placeholders. For example for modpack version.
    - For usage or more info see config comment of `text.modpack_name`
- Drag and Drop support. Now files can be dragged and dropped directly from gui.
    - If dragged and dropped `Avalible log files:`, all logs will be dropped at once.
- Added requested by Modded Minecraft Discord warning about their logs sharing policy. If discord link is default(moddedmc).
- Small fixes.

1.2.6:
- Done a lot of work to prevent posting screenshot of GUI instead of generated msg:
    - `Upload all...` and `$CONFIG.text.support_name$` of commentLabel are now hyperlinks. Pressing them will result blinking for 3 seconds of according button background with light red.
    - Then upload finished: `Copied!` button background will blink with light green for 3 seconds to request user attention.
    - Added under comment label optional bold red text `Please read the text above carefully. Screenshot of this GUI, tells us nothing!`. You can disable this with new config option.
- Improved and simplified Environment check to prevent potential issues.
- Small fixes.

1.2.5:
- Fixed grammar in crash jvm command.
- Don't load mod instead of crashing dedicated server.
- Improve modlist diff dialog.
- Link in generated msg is now surrounded by <>.
- Small fixes.

1.2.4:
- Neoforge 1.21.1 port.
- Added "the" to upload button text in en_us lang.

1.2.3:
- Marked fabric mod as compatible with Quilt.
- Added args for '/crash_assistant crash' command:
    - --withThreadDump
    - --withHeapDump
    - --GCBeforeHeapDump
- Added `no_crash` for `/crash_assistant crash` command if needed just to get thread dump or heap dump without crashing.
- Fixed incorrect width if comment label is wider than other widgets.
- Old CrashAssistantApp logs now deleted.
- Improved wording and grammar in en and ru lang.
- Prevented starting dedicated server if mod is installed.
- Improved moving to font on gui start algorithm.
- Many small fixes.

1.2.2:
- Fixed critical issue making mod incompatible with very many fabric mods using night-config.
- Added '/crash_assistant crash' command for debug.
- Many fixes.

1.2.1:
- Added Chinese lang.
- Added corrupted lang files handling.
- Expanded fabric version range 1.19.2 - 1.21.4.
- Small fixes.

1.2.0a:
- Localisation
- Bugfix

1.1.1:
- Fixed some options didn't support Chinese.
- Small fixes.

1.1.0:
- Config.
- Modlist.
- Commands.
- Very many changes and fixes.

1.0.1:
- Get rid of fatjar.
- Very many small fixes.

1.0.0:
- initial release