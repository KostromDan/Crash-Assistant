# Scripting Support

Crash Assistant provides a JEXL-based scripting system to analyze logs, mod lists, hardware constraints, JVM/launch arguments, and provide interactive warnings.
Scripts run in a restricted sandbox defined by `jexl_allowed_classes.txt` in the root of the jar. Allowed classes include standard Java utilities (Math, Collections, Stream, Regex) and specific Crash Assistant classes for handling logs, mod lists, and so on.

## Available Script Types

1. **[Log Analysis Scripts](Log%20Analysis%20Scripts.md)**
   * **Execution context:** Triggered after a crash, right before the log analysis stage.
   * **Purpose:** Analyzing logs, adding custom crash warnings.

2. **[Startup Scripts](Startup%20Scripts.md)**
   * **Execution context:** Triggered early in the Minecraft launch sequence.
   * **Purpose:** Checking system memory limits, JVM/launch arguments, mod list constraints, adding startup/crash warnings, and preventing the game from launching if necessary.
