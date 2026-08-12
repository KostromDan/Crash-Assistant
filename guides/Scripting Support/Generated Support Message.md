# Generated Support Message

Startup and log-analysis scripts can add records to the support message copied by the **Upload all** button, insert text into custom template slots, and override the message structure for the current session.

## Adding Results to the Standard Analysis Block

Log-analysis script:

```java
Analysis.putCopiedResult(
    "xyz-disable-ignored",
    "User ignored the prompt to disable XYZ"
);
```

Startup script:

```java
Startup.putCopiedResult(
    "unsupported-java",
    "User launched the modpack with unsupported Java 17"
);
```

No custom placeholder is required. Both records are automatically added to the standard `$ANALYSIS_RESULT$` block:

````text
Modpack XYZ crashed! Crash logs:
### latest.log: https://mclo.gs/example
```ansi
Found 2 log analysis results:
User ignored the prompt to disable XYZ
User launched the modpack with unsupported Java 17
```
````

Available overloads and removal methods:

```java
Analysis.putCopiedResult("id", "Text");
Analysis.putCopiedResult("id", "Text", 100); // Higher priority is rendered first
Analysis.removeCopiedResult("id");

Startup.putCopiedResult("id", "Text");
Startup.putCopiedResult("id", "Text", 100);
Startup.removeCopiedResult("id");
```

An entry is identified by both its script file and `id`. Calling `putCopiedResult` again with the same ID from the same script updates that entry. Different scripts may use the same ID without replacing one another.

The standard analysis block is still controlled by `generated_message.put_analysis_result_to_message`. If that option is disabled or the active message structure omits `$ANALYSIS_RESULT$`, copied results are retained in session state but are not included in the copied message.

## Custom Message Slots

Use a custom slot when text must appear at a specific location instead of inside `$ANALYSIS_RESULT$`.

First add a placeholder to `generated_message.message_structure`:

```toml
message_structure = """
$HEADER$$TEXT_UNDER_CRASHED$$PREFIX$$LOGS$
$ANALYSIS_RESULT$
$CUSTOM/support_details$
$MODLIST_DIFF$
"""
```

Any startup or log-analysis script can then add entries to that slot:

```java
GeneratedMessage.putCustom(
    "support_details",
    "xyz-status",
    "XYZ was not disabled"
);

GeneratedMessage.putCustom(
    "support_details",
    "memory-status",
    "Only 4 GB of RAM was allocated"
);
```

The resulting part of the copied message is:

```text
XYZ was not disabled
Only 4 GB of RAM was allocated
```

Available overloads and removal method:

```java
GeneratedMessage.putCustom("slot", "id", "Text");
GeneratedMessage.putCustom("slot", "id", "Text", 100);
GeneratedMessage.removeCustom("slot", "id");
```

Entries in the same slot are joined with a newline. If the template does not contain `$CUSTOM/support_details$`, entries added to `support_details` have no effect on the copied message.

As with copied analysis results, a custom entry is identified by its script file and ID. The same script updates its existing entry, while entries with the same ID from different scripts coexist.

If a custom placeholder has no entries:

* When the placeholder is the only content on its line, that whole line is removed. It does not leave an empty line.
* When the placeholder is embedded in other text, only the placeholder itself is removed (zero characters are inserted).

For example:

```text
Before
$CUSTOM/missing$
After
```

becomes:

```text
Before
After
```

Values are inserted literally. Placeholder-looking text inside a script-provided value is not evaluated again.

## Overriding the Message Structure from Scripts

Scripts can override `generated_message.message_structure` for the current Crash Assistant session without modifying `config.toml`:

```java
var structure = GeneratedMessage.getCurrentStructure();
GeneratedMessage.overrideStructure(
    structure.replace("$ANALYSIS_RESULT$", "$ANALYSIS_RESULT$\n$CUSTOM/support_details$")
);
```

`GeneratedMessage.getCurrentStructure()` returns:

* the value from `config.toml` if no earlier script has overridden it;
* the latest overridden value if an earlier script has changed it.

This allows multiple scripts to modify the structure sequentially. For example, script A can add one slot, then script B reads script A's result and adds another:

```java
// Script A
GeneratedMessage.overrideStructure(
    GeneratedMessage.getCurrentStructure() + "\n$CUSTOM/pack_status$"
);
```

```java
// Script B sees the structure produced by script A
GeneratedMessage.overrideStructure(
    GeneratedMessage.getCurrentStructure() + "\n$CUSTOM/support_contact$"
);
```

`GeneratedMessage.clearStructureOverride()` removes the override belonging to the current script, revealing the preceding script's override or the configuration value. Standard placeholders such as `$HEADER$`, `$LOGS$`, and `$ANALYSIS_RESULT$`, followed by custom slots, are resolved only after all scripts finish.

Names used for entry IDs and custom slots must contain only ASCII letters, digits, `_`, `-`, or `.`. Script entries and overrides exist only in memory for the current launch. Startup-script state is passed to the standalone Crash Assistant process automatically.
