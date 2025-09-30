Shows a GUI after Minecraft crashes, immediately displaying all affected game/launcher logs, crash reports, or hs_err files. Provides a one-click solution to upload them, copy the link, and perform other actions for easier reporting, debugging, and troubleshooting.

Mod is client-only. On a server, it will do nothing.

If you have any questions, feel free to join my Discord server: https://discord.gg/dV8WFrJZK6

# Features:
## GUI:

![GUI](https://cdn.modrinth.com/data/cached_images/c66b778a301301e5a37ea0a5ac8d86b3f84a5d0a.png)

Supports drag&drop. You can move files directly from the GUI to wherever you want, or drag and drop all logs by dragging them from the “Available log files:” label.

## Log Analysis:

Analyzes logs for the most common reasons for crashes. If detected, displays a dialog with a problem description and solution.

![atio6axx](https://cdn.modrinth.com/data/cached_images/daeefac58b408e34f0be5a086397ddf94247809b.png)

### Supported analysis:

- **hs_err**:
    - `AlLibAlcCleanup` (Some RAM allocation-related cursed issue)
    - `atio6axx.dll` (AMD driver issue)
    - `glfw.dll` (GPU driver issue)
    - `ig7icd64.dll` (Very old Intel integrated GPU driver issue)
    - There is insufficient memory for the Java Runtime Environment to continue.
    - `libasyncProfiler.so.tmp` (Some Spark crash on Java ≥ 22)
    - `jemalloc.dll` (Some cursed issue with the jemalloc memory allocator)
    - `jvm.dll` (Hardware issue or bug in the JVM itself)
    - `libglfw.so` (Common Linux problem)
    - `libopenal.so` (Common Linux problem)
    - `libGLProgrammability.dylib` (User tried to use a shader pack incompatible with macOS)
    - `StubRoutines::SafeFetch32` (macOS ARM — incorrect JDK issue)
    - `nglMultiDrawElementsBaseVertex` (GPU driver issue)
    - `nvoglv64.dll` (Nvidia driver issue)

- **log**:
    - Create 6 addons incompatibility
        - Also suggests analyzing jars to detect addons incompatible with the current version of Create.
    - CTOV without Lithostitched
    - CurseForge corrupted install folder
    - Disk space ended
    - Duplicated mods
    - `KubeJSDataPack` (Error while loading datapack with KubeJS)
    - Language Provider mismatch.
    - Missing Embeddium for Oculus
    - Dependencies conflict and many mod loading issues
    - ModernFix integrated WatchDog (Integrated server deadlocked)
    - OptiFine (Mod is known to cause many issues; suggestions with alternatives)
    - `OutOfMemoryError`
    - `ResourceLocationException`
    - Rubidium (Mod has been abandoned for years and causes many issues)
    - Version 1.21 (Version is outdated; suggestion to switch to 1.21.1 / 1.21.4 / 1.21.5)

- **win_event**:
    - Was closed by Windows (Usually happens when the user presses buttons during loading or closing, it freezes, and the user clicks "End Process")

- **Codex logs analyzer integration**:
    - After uploading, every supported log type will be analyzed and the result displayed. Sometimes Codex messages can be unclear for average users. In such cases, a detailed message with a how-to-fix guide is displayed:
        - Erroring block/entity

This significantly reduces the load on modpack support, as more than 50% of issues are these common problems.

If you don't want some reason to be displayed, you can blacklist it in the config.

## Integrated GPU detection:

Will check if game is running on integrated gpu, while dedicated avalible:

![iGPU](https://cdn.modrinth.com/data/cached_images/59bdcd57f81d332d84c5dcca8d0107d1a24c89dd.png)

## Compatibility:
Runs on an independent process that waits for the Minecraft JVM to stop.
Mod logic is used only for command registration and saving the mod list, so there should be no compatibility issues at all.
It cannot corrupt any crash reports or logs.

## Upload all logs and copy link with one click:

You can upload all available logs with one click and get a neatly formatted message for pasting on GitHub or Discord. If you are a modpack creator, you will always know that all logs are current and have ALL available info without needing to request additional ones. No more begging users for logs.

## Button to join pre-configured Link:

Your modpack players can easily, with one click, find your Discord and request help right after a crash. This, combined with the exceptional ease of log uploading, significantly increases the probability of reporting individual crashes.

For individual downloads or if not configured, will direct users to the modloader Discord user support channel.

## Modlist:
Saves the modpack modlist every time the game is launched by the modpack creator. Additionally, you can manually save the modlist using the `/crash_assistant modlist save` command.

Works differently for modpacks and individual downloads:

#### Modpacks:
The generated message includes a list of mods that the end user has added, removed, or updated.

#### Individual downloads / modpack authors:
Tracks mod list changes since the last successful launch, which can be extremely useful if you hit the "update all" button or added problematic mods and forgot which ones. This eliminates the need to perform an annoying binary search to find them.

## Configuration:
Fully configurable: you can enable or disable any feature, change the Discord link, and customize the text of labels, buttons, and more.

## Localization:

Fully localized, currently supported languages:
- English
- Russian
- Chinese
- Spanish

There are no hardcoded strings (except for logging). You can change every visible string. For more localization info, if you want to add a new language or change some strings for a modpack, read [lang](https://github.com/KostromDan/Crash-Assistant/tree/1.19.2-1.20.1/common_config/src/main/resources/lang).

## Crash Commands:
`/crash_assistant crash` has 3 options:
- `game` - crashes Minecraft with a crash report.
- `jvm` - crashes the JVM with an hs_err_pid.
- `no_crash` - applies additional args without crashing.

After the crash target, you can use additional args:
- `--withThreadDump` - generates a thread dump.
- `--withHeapDump` - generates a heap dump.
- `--GCBeforeHeapDump` - performs garbage collection before the heap dump.

## Partners
YourKit supports open-source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/), and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)

## Privacy
Logs are uploaded **only when user explicitly clicks the “Upload” button**, and they are sent directly to `mclo.gs`, a log-sharing service operated by **Aternos**.

Before publication, `mclo.gs` automatically **redacts sensitive data**—such as access tokens, IP addresses, system usernames, etc—to keep the paste safe to share.

Complete information on retention periods, who can access the logs and your GDPR rights is available in their [privacy policy](https://aternos.gmbh/en/mclogs/privacy).

Because the mobile UI of `mclo.gs` can be inconvenient, Crash Assistant—by default—**wraps** the returned URL through **`gnomebot.dev`** (e.g. `https://mclo.gs/X` → `https://gnomebot.dev/paste/mclogs/X`). You can disable this behaviour in settings. `gnomebot.dev` is an open-source online log viewer created by **Latvian Modder**; it fetches data from `mclo.gs` and displays it to the user with convenient formatting, caches the paste in the RAM for **five minutes** after the last access, then discards it.

Crash Assistant itself is **entirely client-side**—it has no servers and never receives a copy of your logs.