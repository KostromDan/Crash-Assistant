package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TimeZone;

public final class LogReaderCurseForgeStdoutTest {
    private static final String CURSEFORGE_LOG_NAME = "CurseForge: stdout-logs.txt";
    private static final String PHYSICAL_FILE_NAME = "stdout-logs.txt";
    private static final long TEST_TIMESTAMP = 3_723_000L;
    private static final String TEST_TIME = "01:02:03";

    private LogReaderCurseForgeStdoutTest() {
    }

    public static void main(String[] args) throws Exception {
        TimeZone previousTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            testMacSparsePrefixAndPreservedTransportLines();
            testWindowsEveryLinePrefixWithMultilineMessageAndThrowable();
            testLeadingTransportAndLongFirstEvent();
            testMetadataAndFormatGates();
            testRawLineLimitDoesNotSplitNormalizedLog();
            testRawByteLimitDoesNotSplitNormalizedLog();
            testReverseTailUsesNormalizedLines();
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
        System.out.println("CurseForge stdout LogReader tests passed.");
    }

    private static void testMacSparsePrefixAndPreservedTransportLines() throws Exception {
        String contents =
                "[STDOUT]   <log4j:Event logger=\"first.Logger\" timestamp=\"" + TEST_TIMESTAMP
                        + "\" level=\"INFO\" thread=\"main\">\r\n"
                        + "    <log4j:Message><![CDATA[first message\r\n"
                        + "[STDOUT] literal sparse-prefix payload]]></log4j:Message>\r\n"
                        + "  </log4j:Event>\r\n"
                        + "\r\n"
                        + "[STDOUT] Completely ignored arguments: [, , ]\r\n"
                        + "[STDERR] native warning outside Log4j\r\n"
                        + "  <log4j:Event logger=\"second.Logger\" timestamp=\"" + (TEST_TIMESTAMP + 1_000L)
                        + "\" level=\"WARN\" thread=\"Render thread\">\r\n"
                        + "    <log4j:Message><![CDATA[second message]]></log4j:Message>\r\n"
                        + "  </log4j:Event>\r\n"
                        + "[EXIT] code=0, terminatedByApp=false";

        LogReader reader = read(PHYSICAL_FILE_NAME, CURSEFORGE_LOG_NAME, contents);

        assertEquals(
                "[" + TEST_TIME + "] [main/INFO]: first message\n"
                        + "[STDOUT] literal sparse-prefix payload\n"
                        + "[STDOUT] Completely ignored arguments: [, , ]\n"
                        + "[STDERR] native warning outside Log4j\n"
                        + "[01:02:04] [Render thread/WARN]: second message\n"
                        + "[EXIT] code=0, terminatedByApp=false",
                reader.getAllLinesString(),
                "Mac-style transport prefixes must decode to the normal Minecraft log format");
        assertEquals(6, reader.getCountedLines(),
                "Decoded events and preserved top-level transport lines must be counted");
        assertNull(reader.getLastLinesString(), "A short decoded log must not be split");
    }

    private static void testWindowsEveryLinePrefixWithMultilineMessageAndThrowable() throws Exception {
        String contents =
                "\ufeff[STDOUT]   <log4j:Event logger=\"ignored.Logger\" timestamp=\"" + TEST_TIMESTAMP
                        + "\" level=\"ERROR\" thread=\"Render &amp; worker\">\n"
                        + "[STDOUT]     <log4j:Message><![CDATA[first ]]>]]&gt;<![CDATA[line\n"
                        + "[STDOUT] [STDOUT] literal every-line-prefix payload]]></log4j:Message>\n"
                        + "[STDOUT]     <log4j:Throwable><![CDATA[java.lang.IllegalStateException: boom\n"
                        + "[STDOUT] \tat example.Clazz.run(Clazz.java:7)]]></log4j:Throwable>\n"
                        + "[STDOUT]   </log4j:Event>\n"
                        + "[EXIT] code=-1, terminatedByApp=false";

        LogReader reader = read(PHYSICAL_FILE_NAME, CURSEFORGE_LOG_NAME, contents);

        assertEquals(
                "[" + TEST_TIME + "] [Render & worker/ERROR]: first ]]>line\n"
                        + "[STDOUT] literal every-line-prefix payload\n"
                        + "java.lang.IllegalStateException: boom\n"
                        + "\tat example.Clazz.run(Clazz.java:7)\n"
                        + "[EXIT] code=-1, terminatedByApp=false",
                reader.getAllLinesString(),
                "BOM, per-line transport prefixes, multiline messages and throwables must be decoded");
        assertEquals(5, reader.getCountedLines(),
                "Decoded multiline entries and preserved top-level lines must count toward the limit");
        assertEquals((long) reader.getAllLinesString().getBytes(StandardCharsets.UTF_8).length,
                reader.getProcessedLength(), "Processed length must exactly match the decoded UTF-8 text");
        assertNull(reader.getLastLinesString(), "A short decoded multiline log must not be split");
    }

    private static void testMetadataAndFormatGates() throws Exception {
        String valid = oneLineEvent("[STDOUT] ", "gate message", "gate.Logger", TEST_TIMESTAMP);
        String expectedRaw = valid.substring(0, valid.length() - 1);

        LogReader wrongLauncher = read(PHYSICAL_FILE_NAME, "OtherLauncher: stdout-logs.txt", valid);
        assertEquals(expectedRaw, wrongLauncher.getAllLinesString(),
                "A non-CurseForge registration must pass through unchanged");

        LogReader wrongPhysicalName = read("renamed.txt", CURSEFORGE_LOG_NAME, valid);
        assertEquals(expectedRaw, wrongPhysicalName.getAllLinesString(),
                "Only the exact physical stdout-logs.txt filename may be decoded");

        String changedSignature =
                "[STDOUT]   <log4j:Event timestamp=\"" + TEST_TIMESTAMP
                        + "\" logger=\"gate.Logger\" level=\"INFO\" thread=\"main\">\n"
                        + "    <log4j:Message><![CDATA[future format]]></log4j:Message>\n"
                        + "  </log4j:Event>";
        LogReader futureFormat = read(PHYSICAL_FILE_NAME, CURSEFORGE_LOG_NAME, changedSignature);
        assertEquals(changedSignature, futureFormat.getAllLinesString(),
                "An unrecognized future CurseForge schema must pass through unchanged");

        LogReader wrongRegisteredName = read(PHYSICAL_FILE_NAME, "CurseForge: captured output", valid);
        assertEquals(expectedRaw, wrongRegisteredName.getAllLinesString(),
                "Only the exact registered CurseForge log name may enable decoding");
    }

    private static void testLeadingTransportAndLongFirstEvent() throws Exception {
        StringBuilder contents = new StringBuilder("[STDERR] warning before the first event\n");
        contents.append("[STDOUT]   <log4j:Event logger=\"long.Logger\" timestamp=\"")
                .append(TEST_TIMESTAMP)
                .append("\" level=\"INFO\" thread=\"main\">\n")
                .append("    <log4j:Message><![CDATA[first line\n");
        for (int i = 0; i < 80; i++) {
            contents.append("continuation-").append(i).append('\n');
        }
        contents.append("last line]]></log4j:Message>\n")
                .append("  </log4j:Event>\n");

        LogReader reader = read(PHYSICAL_FILE_NAME, CURSEFORGE_LOG_NAME, contents.toString());

        assertTrue(reader.isContentTransformed(),
                "Leading transport lines and a long first Event must still match the current format");
        assertEquals("[STDERR] warning before the first event", reader.getFirstLinesList().get(0),
                "A leading top-level line must be preserved");
        assertEquals(normalizedLine("first line"), reader.getFirstLinesList().get(1),
                "The long first Event must be decoded");
        assertEquals("last line", reader.getLastLine(), "The long first Event must remain complete");
    }

    private static void testRawLineLimitDoesNotSplitNormalizedLog() throws Exception {
        final int eventCount = 8_334;
        StringBuilder contents = new StringBuilder(eventCount * 190);
        for (int i = 0; i < eventCount; i++) {
            contents.append(oneLineEvent("[STDOUT] ", numberedLine(i), "limit.Logger", TEST_TIMESTAMP));
        }

        LogReader reader = read(PHYSICAL_FILE_NAME, CURSEFORGE_LOG_NAME, contents.toString());
        List<String> firstLines = reader.getFirstLinesList();

        assertEquals(eventCount, firstLines.size(),
                "More than 25,000 raw XML lines but fewer decoded lines must stay in one part");
        assertEquals(eventCount, reader.getCountedLines(), "Line count must describe decoded output");
        assertNull(reader.getLastLinesString(), "Raw XML wrapper lines must not trigger tail splitting");
        assertEquals(normalizedLine(numberedLine(0)), firstLines.get(0), "Decoded head line mismatch");
        assertEquals(normalizedLine(numberedLine(eventCount - 1)), firstLines.get(eventCount - 1),
                "Decoded final line mismatch");
    }

    private static void testRawByteLimitDoesNotSplitNormalizedLog() throws Exception {
        final int eventCount = 900;
        StringBuilder logger = new StringBuilder(12_000);
        for (int i = 0; i < 12_000; i++) {
            logger.append('L');
        }
        String loggerName = logger.toString();

        StringBuilder contents = new StringBuilder(eventCount * 12_180);
        for (int i = 0; i < eventCount; i++) {
            contents.append(oneLineEvent("[STDOUT] ", numberedLine(i), loggerName, TEST_TIMESTAMP));
        }
        assertTrue(contents.length() > LogReader.maxUploadLength,
                "The size regression fixture must exceed the raw 10 MiB limit");

        LogReader reader = read(PHYSICAL_FILE_NAME, CURSEFORGE_LOG_NAME, contents.toString());

        assertEquals(eventCount, reader.getFirstLinesList().size(),
                "A raw file over 10 MiB but small decoded text must stay in one part");
        assertNull(reader.getLastLinesString(), "Raw XML bytes must not trigger tail splitting");
        assertTrue(reader.isContentTransformed(), "The size metric must identify transformed content");
        assertTrue(reader.getProcessedLength() < LogReader.maxUploadLength,
                "Processed length must measure decoded UTF-8 output instead of the source file");
    }

    private static void testReverseTailUsesNormalizedLines() throws Exception {
        final int oneLineEventCount = LogReader.maxUploadLines - 1;
        StringBuilder contents = new StringBuilder(oneLineEventCount * 190);
        for (int i = 0; i < oneLineEventCount; i++) {
            contents.append(oneLineEvent("[STDOUT] ", numberedLine(i), "tail.Logger", TEST_TIMESTAMP));
        }
        contents.append("[STDOUT]   <log4j:Event logger=\"tail.Logger\" timestamp=\"")
                .append(TEST_TIMESTAMP)
                .append("\" level=\"ERROR\" thread=\"Render thread\">\n")
                .append("[STDOUT]     <log4j:Message><![CDATA[tail message first\n")
                .append("[STDOUT] tail message second]]></log4j:Message>\n")
                .append("[STDOUT]     <log4j:Throwable><![CDATA[java.lang.RuntimeException: tail\n")
                .append("[STDOUT] \tat example.Tail.run(Tail.java:9)]]></log4j:Throwable>\n")
                .append("[STDOUT]   </log4j:Event>\n")
                .append("[EXIT] code=-1, terminatedByApp=false");

        LogReader reader = read(PHYSICAL_FILE_NAME, CURSEFORGE_LOG_NAME, contents.toString());
        List<String> firstLines = reader.getFirstLinesList();
        List<String> lastLines = reader.lastLines;

        assertEquals(LogReader.maxUploadLines, firstLines.size(), "Decoded head must obey the 25,000-line limit");
        assertEquals(normalizedLine(numberedLine(0)), firstLines.get(0), "Decoded head order mismatch");
        assertEquals("[" + TEST_TIME + "] [Render thread/ERROR]: tail message first",
                firstLines.get(firstLines.size() - 1), "Head must stop on a decoded line, not an XML wrapper");

        assertNotNull(lastLines, "A decoded log over 25,000 lines must have a tail");
        assertEquals(LogReader.maxUploadLines, lastLines.size(), "Decoded tail must obey the 25,000-line limit");
        assertEquals(normalizedLine(numberedLine(4)), lastLines.get(0), "Decoded tail start/order mismatch");
        assertEquals("[" + TEST_TIME + "] [Render thread/ERROR]: tail message first",
                lastLines.get(lastLines.size() - 5), "Reversed reader lost the message prefix");
        assertEquals("tail message second", lastLines.get(lastLines.size() - 4),
                "Reversed reader changed multiline message order");
        assertEquals("java.lang.RuntimeException: tail", lastLines.get(lastLines.size() - 3),
                "Reversed reader changed throwable order");
        assertEquals("\tat example.Tail.run(Tail.java:9)", lastLines.get(lastLines.size() - 2),
                "Reversed reader changed the final stack frame");
        assertEquals("[EXIT] code=-1, terminatedByApp=false", lastLines.get(lastLines.size() - 1),
                "Reversed reader must preserve the final top-level transport line verbatim");
    }

    private static LogReader read(String physicalFileName, String logName, String contents) throws Exception {
        Path directory = Files.createTempDirectory("crash-assistant-curseforge-stdout-test-");
        Path path = directory.resolve(physicalFileName);
        Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        directory.toFile().deleteOnExit();
        path.toFile().deleteOnExit();

        Log log = new Log(LogType.LAUNCHER_LOG, logName, path);
        LogReader reader = log.getReader();
        reader.readLogFile(false);
        return reader;
    }

    private static String oneLineEvent(String transportPrefix, String message, String logger, long timestamp) {
        return transportPrefix + "  <log4j:Event logger=\"" + logger + "\" timestamp=\"" + timestamp
                + "\" level=\"INFO\" thread=\"main\">\n"
                + transportPrefix + "    <log4j:Message><![CDATA[" + message + "]]></log4j:Message>\n"
                + transportPrefix + "  </log4j:Event>\n";
    }

    private static String numberedLine(int index) {
        String number = Integer.toString(index);
        StringBuilder result = new StringBuilder("line-");
        for (int i = number.length(); i < 5; i++) {
            result.append('0');
        }
        return result.append(number).toString();
    }

    private static String normalizedLine(String message) {
        return "[" + TEST_TIME + "] [main/INFO]: " + message;
    }

    private static void assertNull(Object actual, String message) {
        if (actual != null) {
            throw new AssertionError(message + "; actual=" + actual);
        }
    }

    private static void assertNotNull(Object actual, String message) {
        if (actual == null) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "; expected=" + expected + ", actual=" + actual);
        }
    }
}
