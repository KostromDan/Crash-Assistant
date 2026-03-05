package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public class KnownCrashReason {
    protected HashSet<LogType> logTypes;
    protected List<String> patterns;
    protected String message;
    protected int priority;
    protected String dontShowAgainKey = null;
    protected String dontShowAgainCheckboxText = null;
    protected int customOkDelay = -1;
    protected HashSet<String> conflictingReasons = new HashSet<>();
    public static HashSet<KnownCrashReason> shownKnownCrashReasons = new HashSet<>();

    protected LinkedHashMap<String, Consumer<JDialog>> autoFixButtons = new LinkedHashMap<>();

    public KnownCrashReason(LogType logType, String message, List<String> patterns) {
        this.logTypes = new HashSet<LogType>() {{
            add(logType);
        }};
        this.message = message;
        this.patterns = patterns;
    }

    public KnownCrashReason(HashSet<LogType> logTypes, String message, List<String> patterns) {
        this.logTypes = logTypes;
        this.message = message;
        this.patterns = patterns;
    }

    public KnownCrashReason(LogType logType, String message, String... patterns) {
        this.logTypes = new HashSet<LogType>() {{
            add(logType);
        }};
        this.message = message;
        this.patterns = Arrays.asList(patterns);
    }

    public KnownCrashReason(HashSet<LogType> logTypes, String message, String... patterns) {
        this.logTypes = logTypes;
        this.message = message;
        this.patterns = Arrays.asList(patterns);
    }

    public String getReasonName() {
        return getClass().getSimpleName();
    }

    HashSet<LogType> getLogTypes() {
        return logTypes;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public String getMessage() {
        return message;
    }

    public int getPriority() {
        return priority;
    }

    public HashSet<String> getConflictingReasons() {
        return conflictingReasons;
    }

    public LinkedHashMap<String, Consumer<JDialog>> getAutoFixButtons() {
        return autoFixButtons;
    }

    public boolean matches(Log log) {
        return RegexChecker.logContainsOneOfPatterns(log, patterns);
    }

    public String getDontShowAgainKey() {
        return dontShowAgainKey;
    }

    public void setDontShowAgainKey(String dontShowAgainKey) {
        this.dontShowAgainKey = dontShowAgainKey;
    }

    public String getDontShowAgainCheckboxText() {
        return dontShowAgainCheckboxText;
    }

    public void setDontShowAgainCheckboxText(String dontShowAgainCheckboxText) {
        this.dontShowAgainCheckboxText = dontShowAgainCheckboxText;
    }

    public int getOkDelay() {
        return customOkDelay;
    }

    public void setOkDelay(int okDelay) {
        this.customOkDelay = okDelay;
    }

    public KnownCrashReason addGuideButton(String buttonText, String link) {
        autoFixButtons.put(buttonText, dialog -> {
            try {
                dev.kostromdan.mods.crash_assistant.app.gui.ControlPanel.validateIsDomainTrustedAndOpenInBrowser(link);
            } catch (Exception e) {
                dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp.LOGGER.error("Failed to open guide link: " + link, e);
            }
        });
        return this;
    }

    public KnownCrashReason withMemoryAllocationGuide() {
        return addGuideButton(
                dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider.get("gui.guide.memory_allocation"),
                dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider.RAM_ALLOCATION_GUIDE.getLink()
        );
    }

    public KnownCrashReason withJvmArgsGuide() {
        return addGuideButton(
                dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider.get("gui.guide.jvm_args"),
                dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider.JVM_ARGS_GUIDE.getLink()
        );
    }

    public KnownCrashReason withJavaVersionGuide() {
        return addGuideButton(
                dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider.get("gui.guide.java_version"),
                dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider.JAVA_VERSION_GUIDE.getLink()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnownCrashReason that = (KnownCrashReason) o;
        String thisName = getReasonName();
        String thatName = that.getReasonName();
        if (thisName != null ? !thisName.equals(thatName) : thatName != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        String name = getReasonName();
        return name != null ? name.hashCode() : 0;
    }
}
