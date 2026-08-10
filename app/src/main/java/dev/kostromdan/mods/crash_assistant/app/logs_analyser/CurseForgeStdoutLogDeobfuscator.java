package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import org.apache.commons.io.input.ReversedLinesFileReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CurseForgeStdoutLogDeobfuscator {
    private static final String FILE_NAME = "stdout-logs.txt";
    private static final String REGISTERED_LOG_NAME = "CurseForge: stdout-logs.txt";
    private static final String STDOUT_PREFIX = "[STDOUT] ";
    private static final String MESSAGE_START = "    <log4j:Message><![CDATA[";
    private static final String MESSAGE_END = "]]></log4j:Message>";
    private static final String THROWABLE_START = "    <log4j:Throwable><![CDATA[";
    private static final String THROWABLE_END = "]]></log4j:Throwable>";
    private static final String EVENT_END = "  </log4j:Event>";
    private static final int MAX_BUFFERED_EVENT_CHARS = 32 * 1024 * 1024;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final Pattern EVENT_START = Pattern.compile(
            "^  <log4j:Event logger=\"[^\"]*\" timestamp=\"([0-9]+)\" level=\"([A-Z]+)\" thread=\"([^\"]*)\">$");

    private CurseForgeStdoutLogDeobfuscator() {
    }

    static boolean isTarget(Log log) {
        return log.getType() == LogType.LAUNCHER_LOG
                && FILE_NAME.equals(log.getFileName())
                && REGISTERED_LOG_NAME.equals(log.getName());
    }

    static boolean isSupported(BufferedReader reader) throws IOException {
        String firstEventLine;
        while ((firstEventLine = reader.readLine()) != null && !isEventStart(firstEventLine)) {
            if (looksLikeLog4j(firstEventLine)) {
                return false;
            }
        }
        if (firstEventLine == null) {
            return false;
        }

        String line = readTransportLine(reader);
        if (line == null || !line.startsWith(MESSAGE_START)
                || !skipCdata(reader, line, MESSAGE_END)) {
            return false;
        }
        line = readTransportLine(reader);
        if (line != null && line.startsWith(THROWABLE_START)) {
            if (!skipCdata(reader, line, THROWABLE_END)) {
                return false;
            }
            line = readTransportLine(reader);
        }
        return EVENT_END.equals(line);
    }

    private static boolean skipCdata(BufferedReader reader, String firstLine, String end) throws IOException {
        String line = firstLine;
        while (!line.endsWith(end)) {
            line = readTransportLine(reader);
            if (line == null) {
                return false;
            }
        }
        return true;
    }

    private static String readTransportLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        return line == null ? null : withoutTransportPrefix(line);
    }

    static LogLineReader forward(BufferedReader reader) {
        return new EventReader(reader);
    }

    static LogLineReader reversed(ReversedLinesFileReader reader) {
        return new EventReader(reader);
    }

    private static List<String> decodeEvent(List<String> rawEvent) {
        List<String> event = new ArrayList<>(rawEvent.size());
        boolean everyLinePrefixed = true;
        for (String line : rawEvent) {
            if (!withoutBom(line).startsWith(STDOUT_PREFIX)) {
                everyLinePrefixed = false;
                break;
            }
        }
        for (String line : rawEvent) {
            String value = withoutBom(line);
            if (value.startsWith(STDOUT_PREFIX)) {
                String unwrapped = value.substring(STDOUT_PREFIX.length());
                boolean structuralLine = event.isEmpty()
                        || event.size() == 1
                        || rawEvent.size() == event.size() + 1
                        || (!event.isEmpty() && event.get(event.size() - 1).endsWith(MESSAGE_END)
                        && unwrapped.startsWith(THROWABLE_START));
                if (everyLinePrefixed || structuralLine) {
                    value = unwrapped;
                }
            }
            event.add(value);
        }

        Matcher eventStart = EVENT_START.matcher(event.get(0));
        if (!eventStart.matches() || !EVENT_END.equals(event.get(event.size() - 1))) {
            return null;
        }

        CdataSection message = readCdata(event, 1, MESSAGE_START, MESSAGE_END);
        if (message == null || message.lines.isEmpty()) {
            return null;
        }

        CdataSection throwable = null;
        int nextIndex = message.nextIndex;
        if (nextIndex < event.size() - 1 && event.get(nextIndex).startsWith(THROWABLE_START)) {
            throwable = readCdata(event, nextIndex, THROWABLE_START, THROWABLE_END);
            if (throwable == null) {
                return null;
            }
            nextIndex = throwable.nextIndex;
        }
        if (nextIndex != event.size() - 1) {
            return null;
        }

        final long timestamp;
        try {
            timestamp = Long.parseLong(eventStart.group(1));
        } catch (NumberFormatException e) {
            return null;
        }

        final String time;
        try {
            time = TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
        } catch (DateTimeException e) {
            return null;
        }

        List<String> decoded = new ArrayList<>(message.lines.size() + (throwable == null ? 0 : throwable.lines.size()));
        decoded.add("[" + time + "] [" + unescapeXml(eventStart.group(3)) + "/" + eventStart.group(2) + "]: "
                + message.lines.get(0));
        decoded.addAll(message.lines.subList(1, message.lines.size()));
        if (throwable != null) {
            decoded.addAll(throwable.lines);
        }
        return decoded;
    }

    private static CdataSection readCdata(List<String> event, int startIndex, String start, String end) {
        if (startIndex >= event.size() || !event.get(startIndex).startsWith(start)) {
            return null;
        }

        List<String> contents = new ArrayList<>();
        String line = event.get(startIndex).substring(start.length());
        for (int i = startIndex; i < event.size() - 1; i++) {
            if (line.endsWith(end)) {
                contents.add(unescapeCdata(line.substring(0, line.length() - end.length())));
                return new CdataSection(contents, i + 1);
            }
            contents.add(unescapeCdata(line));
            line = event.get(i + 1);
        }
        return null;
    }

    private static boolean isEventStart(String line) {
        return EVENT_START.matcher(withoutTransportPrefix(line)).matches();
    }

    private static boolean isEventEnd(String line) {
        return EVENT_END.equals(withoutTransportPrefix(line));
    }

    private static boolean looksLikeLog4j(String line) {
        return withoutTransportPrefix(line).contains("<log4j:")
                || withoutTransportPrefix(line).contains("</log4j:");
    }

    private static String withoutTransportPrefix(String line) {
        String value = withoutBom(line);
        return value.startsWith(STDOUT_PREFIX) ? value.substring(STDOUT_PREFIX.length()) : value;
    }

    private static String withoutBom(String line) {
        return !line.isEmpty() && line.charAt(0) == '\ufeff' ? line.substring(1) : line;
    }

    private static String unescapeXml(String value) {
        return value.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private static String unescapeCdata(String value) {
        return value.replace("]]>]]&gt;<![CDATA[", "]]>");
    }

    static final class UnsupportedFormatException extends IOException {
        UnsupportedFormatException() {
            super("CurseForge stdout log no longer matches the supported Log4j event format");
        }
    }

    private static final class CdataSection {
        private final List<String> lines;
        private final int nextIndex;

        private CdataSection(List<String> lines, int nextIndex) {
            this.lines = lines;
            this.nextIndex = nextIndex;
        }
    }

    private static final class EventReader implements LogLineReader {
        private final BufferedReader forwardReader;
        private final ReversedLinesFileReader reversedReader;
        private final boolean reversed;
        private final Deque<String> pending = new ArrayDeque<>();

        private EventReader(BufferedReader reader) {
            this.forwardReader = reader;
            this.reversedReader = null;
            this.reversed = false;
        }

        private EventReader(ReversedLinesFileReader reader) {
            this.forwardReader = null;
            this.reversedReader = reader;
            this.reversed = true;
        }

        @Override
        public String readLine() throws IOException {
            while (pending.isEmpty()) {
                String line = readRawLine();
                if (line == null) {
                    return null;
                }
                if (!isBlockBoundary(line)) {
                    if (looksLikeLog4j(line)) {
                        throw new UnsupportedFormatException();
                    }
                    if (!line.isEmpty()) {
                        pending.add(withoutBom(line));
                    }
                    continue;
                }

                List<String> event = new ArrayList<>();
                event.add(line);
                long bufferedChars = line.length();
                if (bufferedChars > MAX_BUFFERED_EVENT_CHARS) {
                    throw new UnsupportedFormatException();
                }
                while (!isOppositeBlockBoundary(event.get(event.size() - 1))) {
                    line = readRawLine();
                    if (line == null) {
                        throw new UnsupportedFormatException();
                    }
                    bufferedChars += line.length() + 1;
                    if (bufferedChars > MAX_BUFFERED_EVENT_CHARS) {
                        throw new UnsupportedFormatException();
                    }
                    event.add(line);
                }
                if (reversed) {
                    Collections.reverse(event);
                }
                List<String> decoded = decodeEvent(event);
                if (decoded == null) {
                    throw new UnsupportedFormatException();
                }
                if (reversed) {
                    Collections.reverse(decoded);
                }
                pending.addAll(decoded);
            }
            return pending.removeFirst();
        }

        private boolean isBlockBoundary(String line) {
            return reversed ? isEventEnd(line) : isEventStart(line);
        }

        private boolean isOppositeBlockBoundary(String line) {
            return reversed ? isEventStart(line) : isEventEnd(line);
        }

        private String readRawLine() throws IOException {
            return reversed ? reversedReader.readLine() : forwardReader.readLine();
        }

        @Override
        public void close() throws IOException {
            if (reversed) {
                reversedReader.close();
            } else {
                forwardReader.close();
            }
        }
    }
}
