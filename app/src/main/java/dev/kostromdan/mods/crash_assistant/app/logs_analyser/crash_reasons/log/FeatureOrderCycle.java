package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.HashSet;

public class FeatureOrderCycle extends KnownCrashReason {
    public FeatureOrderCycle() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.feature_order_cycle"),
                "java\\.lang\\.IllegalStateException: Feature order cycle found",
                "com\\.alcatrazescapee\\.cyanide\\.codec\\.FeatureCycleDetector\\$FeatureCycleException: A feature cycle was found\\."
        );
    }
}