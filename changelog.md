1.10.0 major update:

- Fabric: Moved launch of our process from PreLaunchEntrypoint to a LanguageAdapter
  to run from the first possible point.
  Also fixes Crash Assistant not starting issue if crash happened in some MixinConfigPlugin(before PreLaunchEntrypoint).
- Forge coremod: Returned launch of our process to a constructor (to the first possible point) instead of
  `initialize()`. This was done earlier because some needed params were unavailable that early. But I've found a way to
  parse them. So now I reverted that old change.
- Added Auto-Fix option for the Integrated GPU warning. Now can fix the issue with just one click instead of following a
  quite complex guide for inexperienced users.
- Add clarification for desktop users regarding monitor cable and GPU connection in all translations.
- `IntelChipBugWarning`: Added microcode version diagnostics and improved UI/wording.
  Now notifies users if their current microcode version is affected or not.
- Marked `i9-13950hx` and `i9-13980hx` as affected for `IntelChipBugWarning`, since we've seen crashes caused by them.
- Replaced `gpu-detect-jni.dll` GPU detection with C# based detection to prevent including DLLs in mod.
  We're shipping C# source code, which compiles at runtime using `.NET` features available in any Win 10+ system.
- New log analysis:
    - `LegacyTooManyIds` - Too many ids on 1.12.2 and below.
    - `NeoForgeVersion1_20_1` - Notifies that Neo on 1.20.1 is abandoned and causing many crashes and switching to Forge
      is an official recommendation from Neo on that version.
    - `ConnectorIncompatibleFabricMods` - Detects if tried to run fabric mod with connector, but's it's incompatible and
      won't work. Suggests native forge alternatives if they exist. Currently, Sodium, Iris, Lithium.
- Added new launcherlogs support: ElyPrism, SKLauncher, LegacyLauncher, CrystalLauncher,
  KLauncher, PollyMC, Feather, Technic.
- Fixed TLauncher usage was not detected on MacOS or Linux.
- Added handling for missing or corrupted mixin configuration detection in `MixinApply` log analysis.
- Redesigned GUI analysis, transformed `CreateDependencies` to be able to add new analysis easily and fast.
- Fixed `CreateDependencies` wasn't localized and had many hardcoded strings.
- Fixed `CreateDependencies` wasn't looking to jar in jar of mods causing super rare to not detect problematic mod.
- Fixed `CreateDependencies` was working incorrectly for fabric.
- New GUI analysis tools:
    - `MCreator Mod Detector` - Detects MCreator mods. Was already in `modlist.txt`, but now as separate GUI tool.
    - `Epic Fight mod addons compatibility` - Same as `Create mod addons compatibility` but for Epic Fight mods.
    - `Package/Class Finder` - Helps to find from which mod a class or package came.
    - `Jdeps Dependencies Analysis` - Helps to find which mod depends on a class. This is especially helpful if you see
    - NoClassDefFoundError or ClassNotFoundException in your logs and don’t know which mod requires that missing class.
- Redesigned Analysis menu, now displays a short description.
- Now suggesting to use `Epic Fight mod addons compatibility` in `EpicFightAddons` log analysis.
- 1.12.2 and earlier: Improved `OutOfMemory` analysis recommendations. Now recommending available mods on that version.
- 1.12.2: Added compat with `Improved Cleanroom Relauncher` and `Cleanroom relauncher unofficial`. Fixed starting twice.
- Prevented a Crash Assistant mod file being locked while the crash assistant is running.
- Prevented McLogs from censoring IP-like versions.
- Localization: Added missing, fixed outdated and sorted keys across en, ru, it, es, zh;
- Added German localization. Thanks `MagentaCoding` for making PR.
- Jvm.dll analysis: Added one more possible reason for this.
- Fixed issue with non-ASCII paths causing Crash Assistant not to start. E.g., Cyrillic or Chinese symbols in the path.
- Removed CrashAssistantAgent and refactored classpath handling to improve stability on non-ASCII paths.
- `TerminatedProcessesFinder`: Fixed handling of non-standard datetime formats, which caused PS cmd to ignore
  time filter and grab all available event errors instead of just those from the last 15 seconds.
- Fixed hs_err log can be not added in some rare cases.
- Fixed Jdeps analysis didn't checked version of java, caused analysis detected nothing in case usage of outdated jdk.
- Fixed mod on Quilt mod-loader wasn't working since some version due to bad QuiltZipPath implementation in the loader.
- Fixed mod not working on Lunar Client because Lunar was ignoring PreLaunchEntrypoint.
- Fixed `ResourceLocationException` analysis wasn't triggered in some rare cases.
- Fixed ANSI color formatting was still applied in modlist diff, ignoring `generated_message.color_message` setting.
- Small fixes, formatting cleanups, and internal improvements.
- The mod was relicensed under `KostromDan’s Modded Minecraft License (Version 1.0.0)`. Unfortunately, the LGPL did not
  protect my rights and interests as I expected (it should have covered this case, but CurseForge decided that
  derivative code does not need to comply with LGPL terms and conditions and, moreover, does not need to give any
  attribution to the original project, even allowing others to falsely claim it as their own code). It may be due to
  some confusion in the situation, but it is what it is. I am now initiating a court case, which will cost me a lot of
  money, and that fact is quite disappointing. I am also disappointed that I had to relicense, but I don’t see another
  way. There is a lot of drama. If this drama ever ends, I will most likely revert the license back to LGPL.

1.9.15

- Backported to 1.16.1, 1.16.2, 1.16.3, 1.16.4 (forge)
- Backported to 1.17, 1.17.1, 1.18, 1.18.1, 1.19, 1.19.1 (forge+fabric)
- Backported to 1.20.5 (neo)
- Now Crash Assistant is available for all modloaders on every single version on 1.16.5 - 1.21.8 version range without
  gaps.
- Small fixes and improvements.

1.9.14

- Marked 1.21.8 as compatible.
- 1.7.10: fixed jar name to standard format for Crash Assistant.
- 1.7.10: fixed CPU name wasn't parsing propely and was always undefined.
- 1.7.10: Changed from Cleanroom discord to Legacy Modding discord for 1.7.10, as Cleanroom is a pure 1.12.2 community.
- 1.7.10, 1.12.2: Changed initialization logic to run only on the client side.
- 1.7.10, 1.12.2: Improved `mcmod.info` parsing in `ModDataParser` should fix all parsing issues.
- 1.7.10, 1.12.2: Fixed Crash Assistant can be rarely starting twice due to another mods issue.
- 1.12.2: Fixed Crash Assistant commands not available without cheats.

1.9.13

- Initial release for 1.7.10.
- 1.12.2: Fixed with Cleanroom Relauncher Crash Assistant can be started twice due to it named differently on
  different versions.
- 1.12.2: Fixed due to some mods, hooks from Crash Assistant weren't executed, caused not correct behavior of mod.
- 1.12.2: Removed most coremods and moved to forge events to improve compatibility.

1.9.12

- Finally fixed `gpu-detect-jni.dll` issue. Fixes from previous update fixed "crash assistant not starting issue",
  but haven't actually fixed the root cause of issue, so extremely rarely check of running on integrated gpu wasn't
  working.
- Fixed in 1.12.2 `mcmod.info` parsing issue.

1.9.11

- Fixed a rare issue because of which the crash assistant did not start,
  caused by the switch to `gpu-detect-jni.dll` in 1.9.7. Fixed the cause of issue in `gpu-detect-jni.dll`.
  Also moved GPU detection logic to a separate process from the crash assistant app process to prevent any further
  potential issues which can lead to the crash assistant not starting due to GPU detection failure.
- Updated `ModuleFind` log analysis text, to be more clear.

1.9.10

- Backport to 1.12.2, initial release for 1.12.2
- Fixed a small issue in 1.16.5

1.9.9:

- Added new `MissingIndium` analysis if the Indium mod is missing and it's crashing.
- Added new `ModuleFind` analysis: Module `A` not found, required by `B`.
- Added new `UnsupportedClassVersion` analysis if a mod is compiled with a more recent version of Java.
- Improved `MixinApply` analysis, now detects if mixin config is declared with a more recent version of Java.
- Improved error message for handling multiple Create mods in `CreateDependencies` analysis.
- Fixed Crash Assistant not starting in some rare cases due to thinking it's a normal finish of Minecraft.
- Small fixes and improvements.

1.9.8:

- Fixed `Create6Addons` analysis was a little broken with the 1.9.7 update and wasn't triggered in some cases.
- Fixed MixinApply analysis wasn't triggered in some cases.
- Improved MixinApply analysis.
- Fixed jarInJar names in the MixinApply analysis weren't correct.
- Added some logging.
- Small fixes and improvements.

1.9.7:

- Initial release for 1.21.7.
- Added Italian localization. Thanks `splack01` for making PR.
- `MixinApply` new log analysis. Detects most crashes caused by mixins apply/conflicts.
  Currently, disabled for modpacks as a feature is in the beta stage and there is a lot of work to do.
- `UsedByAnotherProcess` new log analysis. If config locked by another process and it's crashing.
- `GroovyModLoaderIPv6` new log analysis. If GML failed to download some files, and it's crashing due to IPv6 issues.
- `EpicFightAddons` new log analysis. Same as Create6Addons log analysis.
- Replaced back Vulkan GPU detection with `gpu-detect-jni.dll` as CurseForge finally allowed me to do so.
  Decreases mod size from ~1,6 mb to ~600 kb.
- Improved version compatibility checks for Create 6 and Steam and Rails mod in `Create6Addons` analysis.
- Added Modrinth launcher log with actual STDOUT support.
- Refactored domain validation and link opening logic.
- Added `qq.com` to trusted domains.
- Fixed `Create6Addons` analysis wasn't triggered sometimes.
- Fixed `FeatureOrderCycle` analysis wasn't triggered sometimes.
- Fixed `ModDataParser` wasn't parsing some mixin configs.
- Fixed some upload buttons can be rarely not activated.

1.9.6:

- Redesigned `ModDataParser` to parse jar in jar, mixin configs and isMCreator.
- Improved `modlist.txt` log. Now prints as a properly formated table, and with a
  list of mixins, jar in jar and isMCreator.
  Note: `modlist.json` in config keeps the same, this is only about `modlist.txt` log.
- Improved error message on OSHI failed to init because of permission issues and ways to fix it.
- Moved config option `modpack_modlist.force_add_full_modlist_as_log` to `modpack_modlist.add_modlist_txt_as_log`.
  Now enabled by default not only for individual downloads but also for modpacks.
- Refactored TerminatedProcessesFinder.
- Added instruments for LauncherLog analysis.
- Added new `ModuleResolution` log analysis.
- Fixed `MissingEmbeddiumForOculus` analysis not triggered in some rare cases.
- Fixed Upload all button text can be not fit into a window in some langs.
- Fixed some rare issues with toml parsing in 1.16.5.

1.9.5:

- Added 1.21.6 forge support.

1.9.4:

- Fixed a small bug in 1.16.5

1.9.3:

- Initial release for 1.20.2 - 1.20.6 forge and neo.
- Fixed 1.20 wasn't marked as a compatible version while publishing mod. While it was.
- Initial release for 1.21.6 all mod loaders.
- Attempted to fix users posting separate logs instead of using the Upload All Button.
- Added ability to customize font size and color of the upload all and request help buttons.
  Made them bigger and colored by default to request user attention.
- Locating win_events time decreased from 1 minute to 15 seconds to reduce probability of capturing false events.
- Improved greeting message to include instructions for updating modpack Discord link.
- Added a config option `generated_message.color_message` to be able to disable color in generated messages with ANSI.
  Can be needed to be disabled if issues are reported to something not supporting ANSI codeblocks, like GitHub.
- Improved ProblematicModsConfig. Now logging a message from modpack creators.
- Fixed open in browser icon didn't display correctly on Linux/MacOS on all versions / 1.16.5 on all OSes.
- Fixed I forgot to mark 1.18.2+ versions as release from prev update.
- Fixed an extremely rare crash on server environment.
- Small fixes and improvements.

1.9.2:

- Initial release for 1.16.5 (beta)
- 1.18.2+ marked as release, as I see no bugs related to the Java 8 backport (from 1.9.0 was beta).
- Prevent a super rare crash on MacOS if OSHI failed to get the processor name.
- Rearrange args for better readability of CA log.
- Fixed `close old app on new Minecraft instance launch` feature was not working on Linux / MacOS.
- Expanded 1.19.2 forge version range support to older versions of forge.

1.9.1:

- Fixed bug in essential mod modid parsing.

1.9.0:

- Complete Java 8 backport to add 1.16.5 and older support.
- Replaced external McLogs API dependency with custom implementation due to it being incompatible with Java 8.
  Also should fix some connection issues on logs uploading.
- Temporarily removed DirectX based gpu-detect-jni.dll due to CurseForge policy about DLLs which they are going to
  change within a month.
- Temporarily returned Vulkan to the app fatjar. Decreased version to 3.1.0 to decrease Vulkan size as much as possible.
  Currently adds 1 MB to mod jar. Instead of 5 mb as was older.
- Added support for MixerLogger.log log files.
- Added support for an additional Portuguese error message in WasClosedByWindows log analysis.
- Moved 'processor' field from Boot to CrashAssistantApp. Added logging of processor.
- Fixed misordered JVM argument in JarInJarHelper command setup. Xms was above Xmx, which can be confusing while reading
  log.
- Introduced a new crash reason `FeatureOrderCycle` log analysis.
- Introduced a new crash reason `ServerConfigCorrupted` log analysis.

1.8.2:

- Refactored in CreateDependencies missing JDK warning dialog with improved user interface and configuration options.
- Added multiple new fallback options to locate JDK installations:
    - Automatic detection of JDK in common installation directories.
    - Support for selecting an existing JDK installation directory.
    - Option to install Oracle JDK 21 via winget with one button click.
    - Ability to save custom JDK path in local configuration.
- Normalize GPU and renderer names for comparison, as DirectX and lwjgl can return in slightly different format.
- Updated WasClosedByWindows crash reason detection for additional Polish message.
- Add system RAM amount logging, as it can help in helping people with OutOfMemory errors.
- Small fixes and improvements.

1.8.1:

- Pass parent Xmx and Xms args as params to a Crash Assistant process. Log them where.
  As log often doesn't include them, but they can be extremely useful.
- Add them to the generated message if OutOfMemory or InsufficientMemory.
- Implemented loading of Vulkan GPU detection addon. (addon will be released soon)
- Improved GPU detection with better error handling.
- Changed DirectX GPU detection jni implementation to fix duplicated GPUs issue,
  errors while creating a device also caused by duplicated GPUs.
- Enhanced a config file locking mechanism for config access synchronization to fix super rare macOS issue.
- Log OS in the Crash Assistant log.
- Improved error messages for GPU detection failures.
- Removed Java 22 FFM-specific DirectX GPU Detection code, because JNI also will work perfectly.
- Removed some parts of Vulkan I forgot to remove in the previous update (decreased mod size by ~20 KB).
- Small fixes and improvements.

1.8.0:

- Decreased mod size by ~5 MB. From ~5.6 MB to ~650 KB. By removing Vulkan used for getting GPU list with their types,
  used for showing a warning `if Minecraft is running on an integrated GPU while a dedicated GPU is available` feature.
  This is a very common problem for Windows notebooks.
- Vulkan logic was replaced with Windows only DirectX logic (~20 KB in jar zip).
- Cross platform Vulkan logic will be still avalible. We will release soon small addon including just Vulkan lib.
- And If Crash Assistant will see it, old Vulkan logic will be used.
- Always print detected GPUs, since it can be used for finding reason of crash.
- Enhance styling for Privacy Policy dialog buttons.
- Small fixes and improvements.

1.7.28:

- Updated Privacy Policy text to be more clear and prevent potential issues.
- If user accepted old version of Privacy Policy, now will be notified about it and need to accept it again.
- Fixed deadlock instead of showing Privacy Policy acceptance dialog. (was rare)
- Added logging of accepting Privacy Policy.
- Added new analysis of hs_err `Ig7icd64` (old Intel integrated GPU driver crashes).
- Sorted lang files by keys to make it easier to maintain.
- Split lang of privacy policy to couple new keys to prevent too long line, which caused difficulties in editing it.
- Got rid of `aaa_` prefix for priority in coremod name, since it's not needed anymore.
- Fixed broken crash assistant duplicated check due to mod rename in 1.7.18.
- Improved formulation of crash assistant duplicated check.

1.7.27:

- Updated Privacy policy text to be more clear and prevent potential issues.
- Fixed Privacy Policy dialog was not a child of the main frame which caused, so it can be hidden under the main frame,
  which confused users, caused to think GUI "stuck". While it was waiting for acceptance of hidden under the main frame
  Privacy Policy dialog.
- Added missing space in one lang of WasClosedByWindows log analysis.
- Small fixes and improvements.

1.7.26:

- Added couple of new supported langs in WasClosedByWindows error message log analysis.
- Marked mod as release, since I haven't seen any issues for a long time and haven't made any breaking changes
  since the mod's creation. There is no reason to mark it as beta.

1.7.25:

- Added new LanguageProviderMismatch log analysis.
- New Analysis for GPU driver issues log analysis.
  Improved old messages.
- Add some code for further modloading analysis.
- Small fixes and improvements.

1.7.24:

- Prevent duplicated code in app and mod part to reduce mod size.
- Added Italian error to was closed by windows analysis.
- Improved WasClosedByWindows warn formulation. (Added about closing Minecraft)
- Small fixes and improvements.

1.7.23.1:

- 1.18.2 branch-only. I forgot to cherry-pick one commit which must be part of 1.7.23 update.

1.7.23:

- Quick fix to match CurseForge request.
- Include analysis ids in a message.
- Small fixes and improvements.

1.7.22:

- Added new JavaTooHigh log analysis (hs_err).
- Small fixes and improvements.

1.7.21:

- Fixed some issues.

1.7.20:

- Significantly improved Privacy Policy text to prevent potential GDPR issues.
- Moved Privacy Policy to the scrollPane to prevent issues on small displays.
- Add an option for resetting the privacy policy contest to prevent potential GDPR issues.

1.7.19:

- Compat with Vulkan mod.
- "1.21" compat.
- 1.21 log analysis. With suggestion to switch to 1.21.1, 1.21.4 / 1.21.5, as 1.21 is abandoned and not supported.
- Refactor hs_err parsing logic into a dedicated parser.
- Added to the generated message if InsufficientMemory, memory settings (pages size, etc).
- Pass parent started time as arg to prevent mismatch if the game crashed too early.
- Small fixes and improvements.

1.7.18:

- Improved jdeps location in CreateDependencies. Should fix all related issues.
- Color found mod names in CreateDependencies for better readability and preventing confusion.
- Refactored and improved Crash reports locating logic.
- Added support of a new log type: Disconnect Client. minecraft/debug/disconnect-...-client.txt
- Small fixes and improvements.

1.7.17:

- Added malware mods detector to protect users from potentially harmful mods.
- Added new log analysis for nglMultiDrawElementsBaseVertex errors (graphics driver issues).
- Added new log analysis for OpenAL audio library errors (alc_cleanup).
- Significantly improved MissingUnsupportedDependencies warn, now detects a lot more messages.
- Improved Problematic frame location logic. Added handling of some cursed scenario.
- Improved InsufficientMemory analysis. Improved message. Added additional message and detection of page files disabled.
  Added instructions on how to enable back page files.
- Improved jvm.dll crash detection with specific handling for Java 17.0.8.
- Updated privacy dialog with "Remember my choice" option instead of "Don't show again".
- Added logging of Java version for better debugging.
- Added logging of stop function fired boolean.
- Added jdeps locating logging.
- Small fixes and improvements.

1.7.16:

- Small formulation fix.

1.7.15:

- Added privacy policy dialog. User must accept the privacy policy on the first upload.
- Added a config option to disable privacy policy acceptance with a warning about potential legal issues in some
  countries.
- Improved privacy information dialog.
- Improved text of Privacy Policy to match GDPR.
- Fixed a rare synchronization bug in a file upload process.

1.7.14:

- Added a privacy information tab in a menu about mclo.gs to prevent potential issues.

1.7.13:

- Improved Log Analysis: ResourceLocationException, MissingEmbeddiumForOculus, Create6Addons
- Disabled Optifine warn for forge 1.20.2+

1.7.12:

- Improved DuplicatedMods warn (neo compat)
- Added priority system to Log Analyser.
- Added conflicting Crash Reasons logic for Log Analyser.
- Added Optifine warning.
- Added KubeJSDataPack Log Analysis.
- Added ModernFixWatchDog Log Analysis.
- Added MissingUnsupportedDependencies Log Analysis.
- Small fixes and improvements.

1.7.11:

- Fixed 1.7.10 crash bc I accidentally removed one line of code, while was getting rig of duplicated inter process
  communication code.
- Fixed too long lines without manually \n in problematic mods config warn displayed too long.
- Fixed I forgot to uncomment one line in DuplicatedMods before release.

1.7.10:

- Include problematic frame to generated message. New config option for this.
- Rename LogProcessor to LogReader.
- Started redesign of log analysis. Now takes less time.
- New Rubidium warn.
- Improved create mod deps formulations.
- Improve JVM.dll warn text.
- Display mod change recommendations to modpack creators.
- Created joinedWorldSuccessfully boolean to be used in analysis.
- Move process interaction logic to ProcessSignalIO, to remove duplicated code and make it simpler..

1.7.9:

- Pass Minecraft version to app process, to be used in analysis.
- Create6Addons now checking version larger 1.20
- Added maven version comparator to be used in analysis.
- Checking OS on OS specific Crash Reasons to optimise analysis.
- Added links to all mods which we recommend in analysis.
  Now you can click to mod and go to mod page.
- Small fixes and improvements.

1.7.8:

- Initial 1.18.2 backport.
- Added lwjgl natives location fallback algo.
- Fixed one stupid mistake.

1.7.7:

- Added JDK install instruction to Create dependencies analysis if we can't locate jdeps.
- Fixed potential issue with JAVA HOME in Create dependencies analysis.
- Improved text of Create mod dep Analysis to be more clear and prevent potential confusion.
- Fixed iGPU warning on linux/macos. Now should work correct.
- Decreased heap size by 3 mb on app idle phase.
- Add full ctov name to CtovWithoutLithostitched warn to prevent potential confusion.
- Added link to Lithostitched mod to CtovWithoutLithostitched warn.
- Change architecture of LinksProvider to be able to return different links based on conditions.
- Added new crash reason Nvoglv64. Nvidia Driver issue.
- Added new crash reason MacOSIncompatibleShaderDriverIssue. MacOS incompatible shaders.
- Added new crash reason Jvm.dll. Some cursed bug which can be caused by multiple reasons.
- Small fixes and improvements.

1.7.6:

- Prevented confusing error message on macs.
- Improved GUI start time on slow pcs, by delaying computing of mod list diff after start of the GUI.
- Added virtual gpu type detection to Vulkan gpu detector.

1.7.5:

- Improved GUI start time on weak systems.
- Improved ModList diff view in GUI to prevent potential confusion.
- Prevent showing same warn if it found in multiple logs.
- Added to WasClosedByWindows new lang of message regex.
- Small fixes and improvements.

1.7.4:

- Added IntegratedGPUWarning about Integrated GPU used while dedicated exist, on startup.
- Improved CurseForgeCorrupted warning. Added new regex.
- Added new DiskSpaceEnded crash reason detection.
- Added to WasClosedByWindows new lang of message regex.
- Added to screenshot notice and upload all button animated borders to additionally request user attention.
- Config option for animated borders.
- Fixed some formulations.

1.7.3:

- Fixed crash on 1.21.5 fabric due to incorrect jar was published by unified publishing.

1.7.2:

- 1.21.5 initial release for Forge, NeoForge and Fabric.
- Improved start time. Especially if massive logs exist. Now almost no effect.
- Added logging of time of analysing logs for crash reasons.
- Improved logging wording on time of parsing mods metadata time.

1.7.1:

- Small fixes in uploading project.

1.7.0 major update:

- Switch from architectury to multiloader mdk.
  A lot of things changed.
- Global re-structuration of project.
- MinecraftForge 1.21.1 port.
- Fixed rare bug if gui started only because of win_event, analysis didn't triggered.
- Fixed some grammar and wording issues in en.
- Improved logging of showing KnownCrashReason.
- Added new options to file menu:
    - Open mods folder
    - Open config folder
    - Open game folder
- Improved insufficient_memory message (added info about disk ending space).
- Small fixes and improvements.

1.6.4:

- Added new log analysis:
    - MissingEmbeddiumForOculus
    - DuplicatedMods
- Async log analysis. Now logs analysing async.
- Removed some useless debug logging I forgot to remove.
- If closed Create dep analysis while it's not finished, interrupt all analysis processes to not waste user resources
  with useless process.
- Small fixes and improvements.

1.6.3:

- Fixed mistake done in 1.6.2: most log analysis stopped displaying at all.
- Added config for analysis feature:
    - enabled
    - blacklisted analysis list
- Fixed case when Create addons warning displayed then it shouldn't.
- Fixed case when Create addons warning wasn't displayed then it should.
- Small fixes.

1.6.2:

- Minor optimisations and fixes.
- Added log analysis for ctov without lithostitched.
- Added File menu. Currently one option to open config.
- Added Analysis menu. Currently one option to analyse Create mod deps.
    - Added new gui for analysis which Create addons not compatible with current version of Create mod.
- Fixed case when Create addons warning displayed then it shouldn't.
- Fixed case when Create addons warning wasn't displayed then it should.
- Added logging of current java binary path.

1.6.1:

- Added support of CurseForge launcher log on MacOS.
- Now displaying last time of UploadAllButton cooldown for locating terminated processes.
- Added in generated messages count of found potential crash reasons by log analysis.
- Added some logging:
    - Paths of added logs.
    - Path from which CA launched(modpack folder).
    - gameLaunchedSuccessfully boolean variable.
- Fixed I forgot to update 1 ru lang key.
- Fixed case when Create addons warning displayed then it shouldn't.

1.6.0 major update:

- Redesigned very many aspects of mod, to make Log Analysis easy to maintain and develop.
- Added analysis of log for most common crash reasons with solution:
    - hs_err:
        - atio6axx.dll (AMD driver issue)
        - There is insufficient memory for the Java Runtime Environment to continue.
        - jemalloc.dll (Some cursed issue with jemalloc memory allocator)
        - libglfw.so (Common Linux problem)
        - libopenal.so (Common Linux problem)
        - StubRoutines::SafeFetch32 (MacOS ARM - incorrect JDK issue)
    - log:
        - Create 6 addons incompatibility
        - CurseForge corrupted Install folder
        - OutOfMemoryError
        - ResourceLocationException
    - win_event:
        - WasClosedByWindows
- Codex logs analyser integration. Every supported log type will be analysed right after uploading. And if it found some
  problem message will be displayed.
- Sometimes Codex solution message can be unclear for avg user.
  For such cases we added own comment on Codex message, currently:
    - Erroring block/entity.
- WinEvents cleaning: mod will leave only five latest files to prevent too many files in modpack folder.
- Prevent error spam in non Windows systems in CA log, bc of PowerShell not available.
- Now blacklisted logs config option works not by equals(), but startswith().
- Added support of ATLauncher launcher log on MacOS.
- Added i9-14900hx to Intel corrupted processors list.
  It's not officially listed, but we've seen already 2 similar cursed logs with this processor.

1.5.1:

- neoforge - Fixed neoforge version in modlist displayed as mc version in some launchers.
- neoforge - Forgot to mark 1.21.5 as incompatible.

1.5.0:

- Added Spanish localization files. Thanks Rener-py for this.
- Redesign of modlist diff. Should fix all problems with updated mods not working correctly when duplicated mods are
  present, some another issues.
- Added messed up mods with version handling in updated mods. If it is, jar name will be displayed instead of incorrect
  version.
- Fixed stupid issue caused incorrect order of parsing mods.toml. Caused rare incorrect version in updated mods.
- Added option modpack_modlist.add_modloader_jar_name, enabled by default, which will add the modloader jar name to the
  modlist, making it easier to track if the user changed the modloader version.
- Added option modpack_modlist.force_add_full_modlist_as_log, which will add the full modlist.txt as a log. Enabled by
  default for individual downloads and disabled for modpacks; change to true to enable for modpacks.
- Improved check for duplicated Crash Assistant mod.
- Fixed issue where I forgot to update the license in mod data.
- Added some logging.
- Marked 1.21.5 as incompatible. I will port to 1.22, but 1.21.5 is not mainstream, will never be it and where too much
  changes. If you need it, feel free to open an issue, maybe I'll change my position.

1.4.3:

- Fixed crash on forge. Caused by empty folder which I forgot to remove.

1.4.2:

- Introducing `problematic_mods_config.json`, where you can configure mods which are incompatible with this modpack.
    - Option to crash game on very early loading if problematic mod detected.
    - Notify user with custom message in incompatible mod detected.
- Fixed reading logs with line with super long length (like 10M chars) at the end took to muck time. Now superfast.
- Update zh_cn.json
- Redesigned moddata parser.

1.4.1:

- Fixed I forgot to remove increased time of locating win_events for debug.

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
    - Replaces "\n" separator between logs to "|" to make message vertically smaller. Enabled by default.
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
    - Added config option enabled by default `generated_message.h3_prefix`: will make logs links in msg bigger, to be
      pleasant for mobile users.
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

- Fixed app process won't finish / start GUI if Minecraft PID was reused immediately by another process. Mostly Linux
  problem. Maybe super rare Windows, but I haven't seen a single case.
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
- Moved `generated_msg_includes_info_why_split` config option from `general` to `generated_message`. Option in `general`
  will void.
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

- Too big logs, which exceeding mclo.gs limits will be split into 2 parts: first and last lines containing 25k lines or
  10MB.
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
- If generatedMsg + modlistDiff is larger than 2000 chars, modlistDiff will be uploaded to mclo.gs to fit non Nitro
  Discord limits.
- Fixed config comments hash wasn't updated properly.
- BCC config integration. Now you can use values from it as placeholders. For example for modpack version.
    - For usage or more info see config comment of `text.modpack_name`
- Drag and Drop support. Now files can be dragged and dropped directly from gui.
    - If dragged and dropped `Avalible log files:`, all logs will be dropped at once.
- Added requested by Modded Minecraft Discord warning about their logs sharing policy. If discord link is default(
  moddedmc).
- Small fixes.

1.2.6:

- Done a lot of work to prevent posting screenshot of GUI instead of generated msg:
    - `Upload all...` and `$CONFIG.text.support_name$` of commentLabel are now hyperlinks. Pressing them will result
      blinking for 3 seconds of according button background with light red.
    - Then upload finished: `Copied!` button background will blink with light green for 3 seconds to request user
      attention.
    - Added under comment label optional bold red text
      `Please read the text above carefully. Screenshot of this GUI, tells us nothing!`. You can disable this with new
      config option.
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
