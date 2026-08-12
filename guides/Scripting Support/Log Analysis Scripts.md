# Log Analysis Scripts

Log Analysis scripts execute right before crash assistant log analysis, giving you ability to analyse logs and add warnings.

To conveniently debug log analysis scripts you can use built-in IDE (`file -> Scripts IDE`). If you don't see this option you need to add your username to modpack creators in the crash assistant config as end users of modpacks won't see it.

## API Documentation

### Accessing Logs
* `LogsList.getLogs()`: Returns a `Set<Log>` of all collected logs.
* `LogsList.getLogs(LogType... types)`: Returns a `List<Log>` filtered by specific types.
    * Examples of `LogType`: `LOG`, `CRASH_REPORT`, `HS_ERR`, `STDERR_STREAM`, `WIN_EVENT`, `LAUNCHER_LOG`.

### `Log` Object
* `log.getName()`: Returns the logical name of the log `String` (e.g. `CurseForge: launcher_log.txt`).
* `log.getFileName()`: Returns `String` (e.g. `launcher_log.txt`).
* `log.getType()`: Returns the `LogType`.
* `log.getReader()`: Returns the `LogReader` instance for the file.

### Reading Log Content (`LogReader`)
* `log.getReader().getAllLinesString()`: Returns the entire log content as a single `String`.
* `log.getReader().getAllLinesList()`: Returns `List<String>` of all log lines.
* `log.getReader().getFirstNLines(int n)`: Returns the first `n` lines as `List<String>`.
* `log.getReader().getLastNLines(int n)`: Returns the last `n` lines as `List<String>`.
* `log.getReader().getFirstLine()`: Returns the first line as `String`.
* `log.getReader().getLastLine()`: Returns the last line as `String`.

### Regex Verification (`RegexChecker`)
* `RegexChecker.logContainsOneOfPatterns(Log log, String... patterns)`: Returns `true` if *any* of the provided Regex patterns match within the log’s text.

### Version Comparison (`VersionUtils`)
* `VersionUtils.isLower(String version1, String version2)`: Returns `true` if `version1` is less than `version2` using Maven's comparable formats.
* `VersionUtils.isLowerThanOrEqual(String version1, String version2)`: Returns `true` if `version1` is <= `version2`.
* `VersionUtils.isGreater(String version1, String version2)`: Returns `true` if `version1` is greater than `version2`.
* `VersionUtils.isGreaterThanOrEqual(String version1, String version2)`: Returns `true` if `version1` is >= `version2`.
* `VersionUtils.isEqual(String version1, String version2)`: Returns `true` if `version1` equals `version2`.
* `VersionUtils.inRange(String version, String minVersion, String maxVersion)`: Returns `true` if `version` falls inside the bounding ranges.

### Java Crash Parsing (`HsErrParser`)
* `HsErrParser.hsErrContainsOneOfFrames(Log log, String... frames)`: Returns `true` if the JVM crash log's problematic frame matches any of the given frame names.
* `HsErrParser.hsErrContainsAllOfFrames(Log log, String... frames)`: Returns `true` if ALL provided frames match inside the problematic frame block.

### Creating Warnings (`Analysis`)
* `Analysis.addWarning(String message)`: Creates a global warning not tied to a specific log (Use only if there is really no log object attached to this warning. Otherwise use the next option.). Returns `ScriptWarning`.
* `Analysis.addWarning(Log log, String message)`: Creates a warning attached to `log`. Returns `ScriptWarning`.
* `Analysis.putCopiedResult(String id, String text)`: Adds or updates a short record inside the copied message's standard `$ANALYSIS_RESULT$` block.
* `Analysis.putCopiedResult(String id, String text, int priority)`: Same as above; higher-priority records are rendered first.
* `Analysis.removeCopiedResult(String id)`: Removes the current script's copied result with this ID.
* `Analysis.markRunAlways()`: Forces the script to run repeatedly on subsequent analysis passes (e.g. when late logs arrive, like `WIN_EVENT`).
* `Analysis.setGlobal(String key, Object value)`: Persists an object across execution passes.
* `Analysis.getGlobal(String key)`: Retrieves a persisted object.

See [Generated Support Message](Generated%20Support%20Message.md) for output examples, custom `$CUSTOM/name$` slots, and session-only message-structure overrides.

### Warning Configuration (`ScriptWarning`)
The object returned by `Analysis.addWarning(…)` allows method chaining to configure the warning popup.

* `withPriority(int priority)`: Sets warning priority (default `10000`). Warnings with higher priorities appear first.
* `withDontShowAgain(String configKey)`: Adds a "Don't show again" checkbox tied to `configKey`.
* `withCustomDontShowAgainCheckboxText(String text)`: Modifies the "Don't show again" checkbox text to the custom one.
* `withOkDelay(int seconds)`: Disables the "OK/Close" button for the specified duration (in seconds).
* `withModActions(Mod mod)`: Renders functional contextual action buttons (Remove, Disable, Show in Explorer) targeting the passed `Mod`.
    * Specific action toggles: `withRemoveButton(boolean)`, `withDisableButton(boolean)`, `withExplorerButton(boolean)`. They are enabled by default use for disabling.
* `withShowModListDiffButton()`: Adds a button that opens the Mod List Diff dialog so users can compare expected vs current mod setup.
* `addGuideButton(String buttonText, String url)`: Adds a custom guide button, which will open the given URL in the default browser.
* `withMemoryAllocationGuide()`: Adds a pre-configured guide button explaining how to manage RAM allocation.
* `withJvmArgsGuide()`: Adds a pre-configured guide button explaining how to manage JVM arguments.
* `withJavaVersionGuide()`: Adds a pre-configured guide button explaining how to change the Java version.

### Script Logging (`Logger`)
* `Logger`
  * In Log Analysis stage, it routes to `crash_assistant_app.log`.
  * Provides static `info(...)`, `warn(...)`, and `error(...)` methods with common Log4j overloads.

### Environment Evaluation
* `PlatformHelp.isWindows()`, `PlatformHelp.isMacOS()`, `PlatformHelp.isLinux()`: Evaluates the operating system in use.

> [!WARNING]
> `ArgUtils` and `MemoryUtils` within the Log Analysis context return settings for the **Crash Assistant** process. To access Minecraft's environment settings, use:
> * `Boot.MINECRAFT_JVM_ARGS` / `Boot.MINECRAFT_LAUNCH_COMMAND`: `String` JVM arguments and full launch command of the Minecraft process.
> * `CrashAssistantApp.minecraftXmx` / `CrashAssistantApp.minecraftXms`: Exact Xmx/Xms values passed to Minecraft. (String, e.g. `2.5g`)
> * `CrashAssistantApp.processor`: CPU model name as a `String` (e.g. `"12th Gen Intel(R) Core(TM) i7-12700H"`).
> * `CrashAssistantApp.renderer`: The GPU/Driver string Minecraft actually used as a `String` (e.g. `"NVIDIA GeForce RTX 3060/PCIe/SSE2"`).

### Hardware Inspection (`MemoryUtils`)
* `MemoryUtils.getSystemTotalMemoryBytes()`: Physical machine RAM (bytes).
* `MemoryUtils.getSystemUsedMemoryBytes()`: Physical machine RAM currently used (bytes).
* `MemoryUtils.getSystemFreeMemoryBytes()`: Physical machine RAM currently unused (bytes).
* `MemoryUtils.getSystemTotalSwapBytes()`: Physical machine swap/pagefile size (bytes).
* `MemoryUtils.getSystemUsedSwapBytes()`: Physical machine swap/pagefile currently used (bytes).
* `MemoryUtils.getSystemFreeSwapBytes()`: Physical machine swap/pagefile currently unused (bytes).
* `MemoryUtils.bytesToMegabytes(long bytes)`: Converts bytes to megabytes (returns `double`).
* `MemoryUtils.bytesToGigabytes(long bytes)`: Converts bytes to gigabytes (returns `double`).
* `MemoryUtils.formatMemorySize(long bytes)`: Formats memory size to human-readable string (e.g., "512m", "2.5g").
* `MemoryUtils.parseMemorySize(String formattedSize)`: Parses formatted memory string back into bytes.

### Mod Ecosystem Evaluation (`ModListUtils`)
* `ModListUtils.getCurrentModList(true)`: Retrieves a `LinkedHashSet<Mod>` representing all active instance mods, datapacks, and resource packs.

### `Mod` Data Object
* `mod.getJarName()`: File name of the mod.
* `mod.getModId()`: The internal Mod ID.
* `mod.getVersion()`: The declared mod version.
* `mod.IsMCreator()`: Returns `true` if the mod is detected as an MCreator mod.


---

## Script Examples

### Example 1: Simple Regex Check
Searches specific patterns within crash reports to detect a manually triggered crash.

```java
// Request crash reports from the gathered logs
var requested_logs = LogsList.getLogs(CRASH_REPORT);

for (log : requested_logs) {
    // Check if the log contains either of the provided strings/regex
    if (RegexChecker.logContainsOneOfPatterns(log, "Description: Manually triggered debug crash", "Caused by: java\\.lang\\.Throwable: Manually triggered debug crash")) {
        // Create a warning using a localized string and attach it to the current log
        var warn = Analysis.addWarning(log, LanguageProvider.get("custom.manual_crash"));
        
        // Add a "Don't show again" option
        warn.withDontShowAgain("manual_crash_warning");
        
        // Ensure this warning is displayed at the top by increasing priority
        warn.withPriority(10001);
    }
}
```

### Example 2: Data Extraction using Java Regex
Uses standard Java classes (`Pattern` and `Matcher`) to extract specific data natively from the log file.

```java
var logs = LogsList.getLogs(LOG);

var pattern = Pattern.compile("Fabric loader version: (\\d+\\.\\d+\\.\\d+)");

for (log : logs) {
    var matcher = pattern.matcher(log.getReader().getAllLinesString());
    
    // If pattern found, extract the exact version group and emit warning
    if (matcher.find()) {
        Analysis.addWarning(log, "Detected Fabric loader version: " + matcher.group(1));
    }
}
```

### Example 3: Iterating Line-by-Line with Context
Sometimes you need to find a specific string, and then inspect the lines *after* it to confirm the crash. This requires iterating using an index.

```java
var crash_logs = LogsList.getLogs(CRASH_REPORT);

for (log : crash_logs) {
    var lines = log.getReader().getAllLinesList();
    
    // Iterate line by line using an index so we can look ahead
    for (var i = 0; i < lines.size(); i++) {
        var line = lines.get(i);
        
        // Find the start of a config loading exception
        if (line.contains("ConfigLoadingException: Failed loading config file ") &&
            line.contains(".toml of type SERVER for modid ")) {
            
            // We found the error header. Extract the Mod ID and Config path by splitting the string.
            var modId = line.split("\\.toml of type SERVER for modid ")[1];
            var configPath = line.split(": Failed loading config file ")[1].split(" of type SERVER for modid ")[0];

            // Look ahead in the following lines to confirm the actual parsing exception
            for (var j = i + 1; j < lines.size(); j++) {
                if (lines.get(j).contains("Caused by: com.electronwill.nightconfig.core.io.ParsingException: ")) {
                    Analysis.addWarning(log, "Server config " + configPath + " for mod " + modId + " is corrupted. Please delete it.");
                    break;
                }
            }
            break; // Stop parsing lines once the issue is found and handled
        }
    }
}
```

### Example 4: Multi-Log Analysis
Iterates `latest.log` to locate a specific variable (like a driver version) using regex. Afterwards, checks the Java crash log (`hs_err`) to verify if that driver caused the crash, then points the warning to the second log.

```java
// Fetch both logs required for analysis
var latest_logs = LogsList.getLogs(LOG);
var hs_err_logs = LogsList.getLogs(HS_ERR);

var driver_version = null;

// [16:03:03] [EarlyDisplay/INFO]: GL info: NVIDIA GeForce RTX 5090 ... GL version 4.6.0 NVIDIA 576.65, NVIDIA Corporation
var pattern = Pattern.compile("GL version .* NVIDIA (\\d+\\.\\d+),");

for (log : latest_logs) {
    var matcher = pattern.matcher(log.getReader().getAllLinesString());
    if (matcher.find()) {
        driver_version = matcher.group(1).trim();
        break;
    }
}

// Proceed only if the driver version was extracted AND it is strictly older than a version containing a theoretical fix
if (driver_version != null && VersionUtils.isLower(driver_version, "577.00")) {
    for (err_log : hs_err_logs) {
         // Cross-reference the Java crash log using semantic HsErr parsing utility
         if (HsErrParser.hsErrContainsOneOfFrames(err_log, "nvoglv64.dll", "nvwgf2umx.dll")) {
             var warn = Analysis.addWarning(err_log, "Crash caused by outdated NVIDIA Graphics driver (Version " + driver_version + "). Please update to 577.00+.");
             warn.withPriority(15000);
         }
    }
}
```
