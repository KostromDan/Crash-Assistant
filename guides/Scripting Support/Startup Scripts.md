# Startup Scripts

Startup scripts execute at the absolute beginning of the Minecraft launch sequence. Use them for checking system memory limits, JVM/launch arguments, mod list constraints, adding startup/crash warnings, and preventing the game from launching if necessary.

The startup scripts are expected to be debugged by a launching minecraft process, since you immediately will see results. Don't try to use scripts IDE for debugging them, it won't work. It's only for log analysis scripts.

## API Documentation

### Throwing Launch Warnings (`Startup`)
* `Startup.addBootWarning(String message)`: Adds warning that appears during startup. Minecraft launch continues. Returns `ScriptWarning`.
* `Startup.addCrashWarning(String message)`: Adds warning that appears after crash. Returns `ScriptWarning`. Doesn't crash game by itself, use next option for it.
* `Startup.markForCrash()`: Terminates the Minecraft JVM client forcefully once all scripts are executed.

> **⚠️ CRITICAL WARNING:** If you use `markForCrash()` alongside `withDontShowAgain()`, you MUST explicitly check `CrashAssistantLocalConfig.get("your_dont_show_again_key")` in your script's logic before calling `markForCrash()`. Otherwise, when the user clicks "Don't Show Again", the warning popup will be hidden on the next launch, but your script will still silently crash the game!

### Warning Configuration (`ScriptWarning`)
*(Methods are shared with Log Analysis tools)*

* `withPriority(int priority)`: Determines sort order (default `10000`).
* `withDontShowAgain(String configKey)`: Adds a "Don't show again" checkbox tied to `configKey`. Settings are saved to `CrashAssistantLocalConfig`.
* `withCustomDontShowAgainCheckboxText(String text)`: Allows customizing the label of the "Don't show again" checkbox.
* `withOkDelay(int seconds)`: Adds a mandatory delay timeout to the "OK" button (forces the user to wait `n` seconds to proceed/close).
* `withModActions(Mod mod)`: Renders functional contextual action buttons (Remove, Disable, Show in Explorer) targeting the passed `Mod`.
  * Specific action toggles: `withRemoveButton(boolean)`, `withDisableButton(boolean)`, `withExplorerButton(boolean)`. They are enabled by default use for disabling.
* `withKillMinecraftButton(boolean enable)`: Adds a button allowing the user to forcefully terminate the Minecraft process.
* `withShowModListDiffButton()`: Adds a button that opens the Mod List Diff dialog so users can compare expected vs current mod setup.
* `addGuideButton(String buttonText, String url)`: Adds a custom guide button, which will open the given URL in the default browser. Checks for trusted domains.
* `withMemoryAllocationGuide()`: Adds a pre-configured guide button explaining how to manage RAM allocation.
* `withJvmArgsGuide()`: Adds a pre-configured guide button explaining how to manage JVM arguments.
* `withJavaVersionGuide()`: Adds a pre-configured guide button explaining how to change the Java version.

### Script Logging (`Logger` and `MinecraftLogger`)
Both classes provide static `info(...)`, `warn(...)`, and `error(...)` methods with common Log4j overloads.

* `Logger`
  * In Startup stage it writes into `startup_scripts.log`.
* `MinecraftLogger`: startup-only logger alias for Minecraft launch flow logging.
  * Writes into `latest.log`.

### Hardware Inspection (`MemoryUtils`)
* `MemoryUtils.getJvmInitialHeapBytes()`: Initial JVM heap size (`-Xms`) in bytes.
* `MemoryUtils.getJvmMaxHeapBytes()`: Total bytes explicitly allocated to the JVM heap (`-Xmx`).
* `MemoryUtils.getJvmAllocatedMemoryBytes()`: RAM currently claimed by the JVM dynamically (bytes).
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

### Version Comparison (`VersionUtils`)
* `VersionUtils.isLower(String version1, String version2)`: Returns `true` if `version1` is less than `version2`.
* `VersionUtils.isLowerThanOrEqual(String version1, String version2)`: Returns `true` if `version1` is <= `version2`.
* `VersionUtils.isGreater(String version1, String version2)`: Returns `true` if `version1` is greater than `version2`.
* `VersionUtils.isGreaterThanOrEqual(String version1, String version2)`: Returns `true` if `version1` is >= `version2`.
* `VersionUtils.isEqual(String version1, String version2)`: Returns `true` if `version1` equals `version2`.
* `VersionUtils.inRange(String version, String minVersion, String maxVersion)`: Returns `true` if `version` falls inside the bounds.

### Environment Evaluation
* `PlatformHelp.isWindows()`, `PlatformHelp.isMac()`, `PlatformHelp.isLinux()`: Evaluates the operating system in use.
* `ArgUtils.getSafeJvmArgs()`: Resolves the JVM configuration arguments assigned to the launch instance. Returns `String`.
* `ArgUtils.getSafeLaunchArgs()`: Resolves the Minecraft program arguments assigned to the instance. Returns `String`. Some sensitive params like access-token are already censored.

### Mod Ecosystem Evaluation (`ModListUtils`)
* `ModListUtils.getCurrentModList(true)`: Retrieves a `LinkedHashSet<Mod>` representing all active instance mods, datapacks, and resource packs.

### `Mod` Data Object
* `mod.getJarName()`: File name of the mod.
* `mod.getModId()`: The internal Mod ID.
* `mod.getVersion()`: The declared mod version.
* `mod.IsMCreator()`: Returns `true` if the mod is detected as an MCreator mod.

---

## Script Examples

### Example 1: Validating Client Machine Over-Allocation
Compares explicit JVM Heap configuration arguments to the physical limitations of the host machine hardware.

```java
var xmxBytes = MemoryUtils.getJvmMaxHeapBytes();
var totalSystemCapacity = MemoryUtils.getSystemTotalMemoryBytes() + MemoryUtils.getSystemTotalSwapBytes();

if (xmxBytes > totalSystemCapacity) {
    var msg = "You've allocated " + MemoryUtils.formatMemorySize(xmxBytes) + 
              ", but your system only has " + 
              MemoryUtils.formatMemorySize(totalSystemCapacity) + " available!";
    
    var warn = Startup.addBootWarning(msg);
    warn.withDontShowAgain("ram_over_allocation");
    warn.withMemoryAllocationGuide();
}
```

### Example 2: JVM Parameter Recommendation
Warns users allocated too much RAM without ZGC arg

```java
var xmxGB = MemoryUtils.bytesToGigabytes(MemoryUtils.getJvmMaxHeapBytes());
var jvmArgs = ArgUtils.getSafeJvmArgs();

if (xmxGB > 12.0 && !jvmArgs.contains("-XX:+UseZGC")) {
    var warn = Startup.addBootWarning("For heap sizes over 12GB, we heavily advise adding -XX:+UseZGC to JVM arguments.");
    warn.withDontShowAgain("zgc_recommend");
    warn.withJvmArgsGuide();
}
```

### Example 3: Resolving Mod List Incompatibilities 
Marking mod incompatible with way to bypass.

```java
var allMods = ModListUtils.getCurrentModList(true);
var mods = allMods.stream().toMap(m -> m.modId, m -> m);

var badMod = mods.get("problematic_mod_id");

if (badMod != null) {
    var bypassKey = "problematic_mod_bypass_key";
    var isBypassed = Objects.equals(CrashAssistantLocalConfig.get(bypassKey), true);
    
    var warn = Startup.addCrashWarning(badMod.jarName + " will corrupt your worlds!");
    
    warn.withModActions(badMod)
        .withDontShowAgain(bypassKey)
        .withCustomDontShowAgainCheckboxText("I accept the risks of corruption, let me play")
        .withOkDelay(15);
    if (!isBypassed) {
        Startup.markForCrash();
    }
}
```
