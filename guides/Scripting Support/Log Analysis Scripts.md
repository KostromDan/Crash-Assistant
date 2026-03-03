# Log Analysis Scripts

Log Analysis scripts execute during issue detection, giving you full access to gathered log files, search utilities, and warning creation mechanisms.

## API Documentation

### Accessing Logs
* `LogsList.getLogs()`: Returns a `Set<Log>` of all collected logs.
* `LogsList.getLogs(LogType... types)`: Returns a `List<Log>` filtered by specific types.
    * Examples of `LogType`: `LOG`, `CRASH_REPORT`, `HS_ERR`, `STDERR_STREAM`, `WIN_EVENT`, `LAUNCHER_LOG`.

### `Log` Object
* `log.getName()`: Returns the logical name of the log `String` (e.g. `latest.log`).
* `log.getFileName()`: Returns `String` (e.g. `latest.log`).
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
* `RegexChecker.logContainsOneOfPatterns(Log log, String... patterns)`: Returns `true` if *any* of the provided Regex patterns match within the log’s text. Does not perform regex extraction.

### Version Comparison (`VersionUtils`)
* `VersionUtils.isLower(String version1, String version2)`: Returns `true` if `version1` is less than `version2` using Maven's comparable formats.
* `VersionUtils.isLowerThanOrEqual(String version1, String version2)`: Returns `true` if `version1` is <= `version2`.
* `VersionUtils.isGreater(String version1, String version2)`: Returns `true` if `version1` is greater than `version2`.
* `VersionUtils.isGreaterThanOrEqual(String version1, String version2)`: Returns `true` if `version1` is >= `version2`.
* `VersionUtils.isEqual(String version1, String version2)`: Returns `true` if `version1` equals `version2`.
* `VersionUtils.inRange(String version, String minVersion, String maxVersion)`: Returns `true` if `version` falls inside the bounding ranges.

### Java Crash Parsing (`HsErrParser`)
* `HsErrParser.parseHsErr(Log log)`: Returns an `Optional<HsErrParsingResult>` extracting hardware configuration (memory/page files) and problematic frames.
* `HsErrParser.hsErrContainsOneOfFrames(Log log, String... frames)`: Returns `true` if the JVM crash log's problematic DLL frame matches any of the given frame names.
* `HsErrParser.hsErrContainsAllOfFrames(Log log, String... frames)`: Returns `true` if ALL provided frames match inside the problematic frame block.

### Creating Warnings (`Analysis`)
* `Analysis.addWarning(String message)`: Creates a global warning not tied to a specific log. Returns `ScriptWarning`.
* `Analysis.addWarning(Log log, String message)`: Creates a warning attached to `log`. Returns `ScriptWarning`.
    * You can use `LanguageProvider.get("custom.key")` to fetch localized messages.
* `Analysis.markRunAlways()`: Forces the script to run repeatedly on subsequent analysis passes (e.g. when late logs arrive).
* `Analysis.setGlobal(String key, Object value)`: Persists an object across execution passes.
* `Analysis.getGlobal(String key)`: Retrieves a persisted object.

### Warning Configuration (`ScriptWarning`)
The object returned by `Analysis.addWarning(…)` allows method chaining to configure the warning popup.

* `withPriority(int priority)`: Sets warning priority (default `10000`). Warnings with higher priorities appear first.
* `withDontShowAgain(String configKey)`: Adds a "Don't show again" checkbox tied to `configKey`.
* `withCustomDontShowAgainCheckboxText(String text)`: Modifies the "Don't show again" checkbox text to the custom one.
* `withOkDelay(int seconds)`: Disables the "OK/Close" button for the specified duration (in seconds).
* `withModActions(Mod mod)`: Injects buttons for managing a problematic mod ("Remove", "Disable", "Show in Explorer").
* `withRemoveButton(boolean enable)`: Explicitly toggles the "Remove" button (requires `withModActions` context).
* `withDisableButton(boolean enable)`: Explicitly toggles the "Disable" button.
* `withExplorerButton(boolean enable)`: Explicitly toggles the "Show in Explorer" button.

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

for (log : logs) {
    // Compile standard Java Pattern (mapped dynamically in JEXL)
    var pattern = Pattern.compile("Fabric loader version: (\\d+\\.\\d+\\.\\d+)");
    
    // Retrieve the entire log as string and feed to Matcher
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

// Iterate LOG files line by line (using an enhanced for-loop)
for (log : latest_logs) {
    var lines = log.getReader().getAllLinesList();
    for (line : lines) {
        // Find driver version relying on the exact log format output:
        // [16:03:03] [EarlyDisplay/INFO]: GL info: NVIDIA GeForce RTX 5090 ... GL version 4.6.0 NVIDIA 576.65, NVIDIA Corporation
        var matcher = Pattern.compile("GL version .* NVIDIA (\\d+\\.\\d+),").matcher(line);
        if (matcher.find()) {
            driver_version = matcher.group(1).trim();
            break; // Stop parsing lines
        }
    }
    if (driver_version != null) {
        break; // Stop parsing logs once found
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
