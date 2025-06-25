package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GroovyModLoaderIPv6 extends KnownCrashReason {
    public GroovyModLoaderIPv6() {
        super(
                LogType.CRASH_REPORT,
                LanguageProvider.get("warnings.groovy_mod_loader_ipv6")
        );
    }

    @Override
    public boolean matches(Log log) {
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("at org.groovymc.gml.mappings.MappingsProvider.downloadFile(MappingsProvider.groovy:")) {
                continue;
            }
            if (i - 3 < 0) {
                return false;
            }
            String stacktraceStartLine = lines.get(i - 3).trim();
            if (!stacktraceStartLine.equals("java.net.ConnectException: null") && !stacktraceStartLine.equals("javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake")) {
                return false;
            }
            List<String> causedByLines = new ArrayList<>();
            for (int j = i + 1; j < lines.size(); j++) {
                String stacktraceLine = lines.get(j);
                if (!isStackTraceLine(stacktraceLine)) {
                    return false;
                }
                if (stacktraceLine.startsWith("Caused by: ")) {
                    causedByLines.add(stacktraceLine.trim());
                    if (causedByLines.size() == 2) break;
                }
            }
            if (causedByLines.size() != 2) return false;
            if (causedByLines.get(0).equals("Caused by: java.net.ConnectException") && causedByLines.get(1).equals("Caused by: java.nio.channels.ClosedChannelException")) {
                return true;
            }
            if (causedByLines.get(0).equals("Caused by: javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake") && causedByLines.get(1).equals("Caused by: java.net.SocketException: Connection reset")) {
                return true;
            }
            break;
        }
        return false;
    }


    private static final Pattern AT_PATTERN = Pattern.compile("^\\s*at\\s+.*");
    private static final Pattern MORE_PATTERN = Pattern.compile("^\\s*\\.{3}\\s+\\d+\\s+more$");
    private static final Pattern CAUSED_BY_PATTERN = Pattern.compile("^\\s*Caused\\s+by:.*");

    private static boolean isStackTraceLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        return AT_PATTERN.matcher(line).matches() ||
                MORE_PATTERN.matcher(line).matches() ||
                CAUSED_BY_PATTERN.matcher(line).matches();
    }
}