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
  - `Upload all...` and `$SUPPORT_NAME$` of commentLabel are now hyperlinks. Pressing them will result blinking for 3 seconds of according button background with light red.
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