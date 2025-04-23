package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.List;
import java.util.Objects;

public class KubeJSDataPack extends KnownCrashReason {
    public KubeJSDataPack() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.kubejs_datapack")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "kubejs"))) return false;
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("IllegalStateException: Failed to parse ") && line.contains(" from pack KubeJS Resource Pack [data]")) {
                line = line.substring(line.indexOf(": ")+2);
                message = message.replace("$LINE_FROM_LOG$", line);
                return true;
            }
        }
        return false;
    }
}