package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.util.Set;
import java.util.regex.Pattern;

public interface IntelCorruptedProcessorChecker {
    Set<String> AFFECTED_MODELS = Set.of(
            "i5-13600k", "i5-13600kf", "i5-14600k", "i5-14600kf",
            "i7-13700", "i7-13700f", "i7-13700k", "i7-13700kf", "i7-13790f",
            "i7-14700", "i7-14700f", "i7-14700k", "i7-14700kf", "i7-14790f",
            "i9-13900", "i9-13900f", "i9-13900k", "i9-13900kf", "i9-13900ks",
            "i9-14900", "i9-14900f", "i9-14900k", "i9-14900kf", "i9-14900ks"
    );

    static boolean isAffectedProcessor() {
        try {
            String model = extractModel(new SystemInfo()
                    .getHardware()
                    .getProcessor()
                    .getProcessorIdentifier()
                    .getName());

            return model != null && AFFECTED_MODELS.contains(model);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while checking processor", e);
            return false;
        }
    }

    private static String extractModel(String cpuName) {
        CrashAssistantApp.LOGGER.info(cpuName);
        var matcher = Pattern.compile("i[579]-\\d+[a-z]*", Pattern.CASE_INSENSITIVE)
                .matcher(cpuName);
        return matcher.find() ? matcher.group().toLowerCase() : null;
    }
}