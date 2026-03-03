# Startup Scripts

Startup scripts execute synchronously at the absolute beginning of the Minecraft launch sequence. They evaluate conditions, hardware limitations, parameters, and mod lists to prevent broken game states.

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
  * Specific action toggles: `withRemoveButton(boolean)`, `withDisableButton(boolean)`, `withExplorerButton(boolean)`.
* `withKillMinecraftButton(boolean enable)`: Adds a button allowing the user to forcefully terminate the Minecraft process.
* `addGuideButton(String buttonText, String url)`: Adds a custom guide button, which will open the given URL in the default browser. Checks for trusted domains.
* `withMemoryAllocationGuide()`: Adds a pre-configured guide button explaining how to manage RAM allocation.
* `withJvmArgsGuide()`: Adds a pre-configured guide button explaining how to manage JVM arguments.

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
// Retrieve limits using MemoryUtils
var xmxBytes = MemoryUtils.getJvmMaxHeapBytes();
var totalSystemCapacity = MemoryUtils.getSystemTotalMemoryBytes() + MemoryUtils.getSystemTotalSwapBytes();

// Check if JVM limit exceeds the physical limits of the OS environment
if (xmxBytes > totalSystemCapacity) {
    var msg = "You've allocated " + MemoryUtils.formatMemorySize(xmxBytes) + 
              ", but your system only has " + 
              MemoryUtils.formatMemorySize(totalSystemCapacity) + " available!";
    
    // Display boot warning (non-fatal) and add "Don't show again" option
    var warn = Startup.addBootWarning(msg);
    warn.withDontShowAgain("ram_over_allocation");
}
```

### Example 2: JVM Parameter Recommendation
Warns users requesting bloated JVM arguments without appropriate Garbage Collector optimizations.

```java
// Convert JVM Xmx constraint into readable GB format
var xmxGB = MemoryUtils.bytesToGigabytes(MemoryUtils.getJvmMaxHeapBytes());
var jvmArgs = ArgUtils.getSafeJvmArgs();

// We determine that anything mapped > 12 GB requires ZGC or performance decreases inherently
if (xmxGB > 12.0 && !jvmArgs.contains("-XX:+UseZGC")) {
    var warn = Startup.addBootWarning("For heap sizes over 12GB, we heavily advise adding -XX:+UseZGC to JVM arguments.");
    warn.withDontShowAgain("zgc_recommend");
}
```

### Example 3: Resolving Mod List Incompatibilities 
Halt application execution if problematic JAR assets are detected dynamically, prompting immediate user interactions for deletion or bypass.

```java
// Map the ModList objects to their IDs for rapid lookups and context validation
var allMods = ModListUtils.getCurrentModList(true);
var mods = allMods.stream().toMap(m -> m.modId, m -> m);

var badMod = mods.get("problematic_mod_id");

if (badMod != null) {
    // Standard explicit evaluation of saved bypass properties
    var bypassKey = "problematic_mod_bypass_key";
    var isBypassed = Objects.equals(CrashAssistantLocalConfig.get(bypassKey), true);
    
    if (!isBypassed) {
        // Emit fatal warning notifying users of failure condition
        var warn = Startup.addCrashWarning(badMod.jarName + " will corrupt your worlds!");
        
        warn.withModActions(badMod) // Provide built-in mod interaction buttons via GUI ("Remove", "Open in Explorer")
            .withDontShowAgain(bypassKey)
            .withCustomDontShowAgainCheckboxText("I accept the risks of corruption, let me play")
            .withOkDelay(15); // Require users to read by forcing a 15-second wait interaction
            
        Startup.markForCrash(); // Inject fatal system halt logic
    }
}
```
