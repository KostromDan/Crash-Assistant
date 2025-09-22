package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced.MixinApply.isInternalClass;
import static dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder.SearchMode.CLASS_OR_PACKAGE;

public class UnsupportedClassVersion extends KnownCrashReason {
    public UnsupportedClassVersion() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.unsupported_class_version"),
                "java\\.lang\\.UnsupportedClassVersionError: ([\\w/]+) has been compiled by a more recent version of the Java Runtime \\(class file version (\\d+)(?:\\.\\d+)?\\), this version of the Java Runtime only recognizes class file versions up to (\\d+)(?:\\.\\d+)?"
        );
    }

    @Override
    public boolean matches(Log log) {
        try {
            String logContent = log.getReader().getAllLinesString();
            Matcher matcher = Pattern.compile(this.patterns.get(0)).matcher(logContent);

            if (matcher.find()) {
                String moduleName = matcher.group(1);
                String compiledVer = matcher.group(2);
                String runtimeVer = matcher.group(3);

                String compiledJavaVersion = Integer.toString(Integer.parseInt(compiledVer) - 44);
                String runtimeJavaVersion = Integer.toString(Integer.parseInt(runtimeVer) - 44);

                if (isInternalClass(moduleName)) return false;

                List<String> jarsContainingModule = ModuleFinder.findJarsInFolderAsync(Collections.singletonList(moduleName), ModListUtils.getCurrentModList(true), CLASS_OR_PACKAGE);

                this.message = this.message
                        .replace("$CLASS_NAME$", moduleName)
                        .replace("$JARS$", "<strong style='color: red;'>" + String.join("\n", jarsContainingModule) + "</strong>")
                        .replace("$COMPILED_VERSION$", compiledJavaVersion)
                        .replace("$RUNTIME_VERSION$", runtimeJavaVersion);

                return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}