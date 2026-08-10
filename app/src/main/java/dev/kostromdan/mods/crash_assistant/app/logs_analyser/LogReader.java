package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.jexl3.annotations.NoJexl;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LogReader {
    final static int maxUploadLines = 25000;
    final static int maxUploadLength = 10485760;
    int countedLines = 0;
    boolean lineCountInterrupted = false;
    List<String> firstLines;
    List<String> lastLines;
    List<String> allLinesListCached;
    String allLinesStringCached;
    Log log;
    boolean isLogProcessed = false;
    boolean contentTransformed = false;
    boolean processedLengthComplete = false;
    long sizeOnLastRead = -1;
    long processedLength = -1;

    @NoJexl
    public LogReader(Log log) {
        this.firstLines = new ArrayList<>(maxUploadLines);
        this.lastLines = null;
        this.log = log;
    }

    @NoJexl
    public synchronized void readLogFile(boolean checkUpdated) throws IOException {
        if (isLogProcessed && !checkUpdated) {
            return;
        }
        long sourceSize = Files.size(log.getPath());
        if (isLogProcessed && sourceSize == sizeOnLastRead) {
            return;
        }

        boolean transformCurseForgeStdout = isCurseForgeStdoutFormatSupported();
        try {
            readLogFile(transformCurseForgeStdout, sourceSize);
        } catch (CurseForgeStdoutLogDeobfuscator.UnsupportedFormatException e) {
            // The format changed after the probe or the file was being written while it was read.
            // Re-read it through the unchanged standard path instead of returning partially decoded data.
            readLogFile(false, sourceSize);
        }
        sizeOnLastRead = sourceSize;
        isLogProcessed = true;
    }

    private void readLogFile(boolean transformCurseForgeStdout, long sourceSize) throws IOException {
        isLogProcessed = false;
        countedLines = 0;
        lineCountInterrupted = false;
        lastLines = null;
        firstLines = new ArrayList<>(maxUploadLines);
        allLinesListCached = null;
        allLinesStringCached = null;
        contentTransformed = transformCurseForgeStdout;
        processedLengthComplete = !transformCurseForgeStdout;
        processedLength = transformCurseForgeStdout ? 0 : sourceSize;

        boolean hasMoreLines = false;
        try (LogLineReader reader = createForwardLineReader(transformCurseForgeStdout)) {
            String line;
            long length = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                int lineLength = getLineContentLength(line);
                recordProcessedLine(lineLength);
                if (firstLines.size() < maxUploadLines && length < maxUploadLength) {
                    if (!firstLines.isEmpty()) {
                        length++;
                    }
                    firstLines.add(line);
                    length += lineLength;
                } else {
                    hasMoreLines = true;
                    break;
                }
            }
            if (!hasMoreLines) {
                processedLengthComplete = true;
                return;
            }

            long timeCountStarted = Instant.now().toEpochMilli();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                recordProcessedLine(contentTransformed ? getLineContentLength(line) : 0);
                if (countedLines % 100 == 0 && Instant.now().toEpochMilli() - timeCountStarted >= 1000) {
                    lineCountInterrupted = true;
                    break;
                }
            }
            if (line == null) {
                processedLengthComplete = true;
            }
        }
        lastLines = new LinkedList<>();
        try (LogLineReader reversedReader = createReversedLineReader(transformCurseForgeStdout)) {
            String line;
            int count = 0;
            long length = 0;
            while ((line = reversedReader.readLine()) != null && count < maxUploadLines && length < maxUploadLength) {
                if (line.isEmpty()) {
                    continue;
                }
                if (count > 0) {
                    length++;
                }
                lastLines.add(0, line);
                length += getLineContentLength(line);
                count++;
            }
            if (length > maxUploadLength && !lastLines.isEmpty()) {
                lastLines.remove(0);
            }
        }
    }

    private void recordProcessedLine(int lineLength) {
        if (contentTransformed) {
            if (countedLines > 0) {
                processedLength++;
            }
            processedLength += lineLength;
        }
        countedLines++;
    }

    private int getLineContentLength(String line) {
        return contentTransformed ? line.getBytes(StandardCharsets.UTF_8).length : line.length();
    }

    private boolean isCurseForgeStdoutFormatSupported() throws IOException {
        if (!CurseForgeStdoutLogDeobfuscator.isTarget(log)) {
            return false;
        }
        try (BufferedReader reader = createBufferedReader()) {
            return CurseForgeStdoutLogDeobfuscator.isSupported(reader);
        }
    }

    private LogLineReader createForwardLineReader(boolean transformCurseForgeStdout) throws IOException {
        BufferedReader reader = createBufferedReader();
        if (transformCurseForgeStdout) {
            return CurseForgeStdoutLogDeobfuscator.forward(reader);
        }
        return new LogLineReader() {
            @Override
            public String readLine() throws IOException {
                return reader.readLine();
            }

            @Override
            public void close() throws IOException {
                reader.close();
            }
        };
    }

    private BufferedReader createBufferedReader() throws IOException {
        return new BufferedReader(new InputStreamReader(
                new FileInputStream(this.log.getFile()), StandardCharsets.UTF_8));
    }

    private LogLineReader createReversedLineReader(boolean transformCurseForgeStdout) throws IOException {
        ReversedLinesFileReader reader = createReversedLinesFileReader();
        if (transformCurseForgeStdout) {
            return CurseForgeStdoutLogDeobfuscator.reversed(reader);
        }
        return new LogLineReader() {
            @Override
            public String readLine() throws IOException {
                return reader.readLine();
            }

            @Override
            public void close() throws IOException {
                reader.close();
            }
        };
    }

    public synchronized void readLogFileSafe() {
        try {
            readLogFile(false);
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.info("Error processing log file", e);
        }
    }

    /**
     * Different versions of common-io having different implementations, so we have to deal with it.
     */
    @NoJexl
    @SuppressWarnings("deprecation")
    private ReversedLinesFileReader createReversedLinesFileReader() throws IOException {
        try {
            return ReversedLinesFileReader.builder()
                    .setPath(this.log.getPath())
                    .setCharset(StandardCharsets.UTF_8)
                    .setBufferSize(1024 * 1024)
                    .get();
        } catch (NoSuchMethodError e) {
            return new ReversedLinesFileReader(log.getFile(), 1024 * 1024, StandardCharsets.UTF_8);
        }
    }

    @NoJexl
    public String getFirstLinesString() {
        return String.join("\n", firstLines);
    }

    @NoJexl
    public List<String> getFirstLinesList() {
        return firstLines;
    }

    @NoJexl
    public String getLastLinesString() {
        return lastLines == null ? null : String.join("\n", lastLines);
    }

    public synchronized String getAllLinesString() {
        synchronized (this) {
            if (allLinesStringCached == null) {
                allLinesStringCached = String.join("\n", getAllLinesList());
            }
            return allLinesStringCached;
        }
    }

    public synchronized List<String> getAllLinesList() {
        synchronized (this) {
            if (allLinesListCached == null) {
                allLinesListCached = new ArrayList<>();
                allLinesListCached.addAll(firstLines);
                if (lastLines != null) allLinesListCached.addAll(lastLines);
            }
            return allLinesListCached;
        }
    }

    @NoJexl
    public synchronized void destroyAllLinesCache() {
        synchronized (this) {
            allLinesStringCached = null;
            allLinesListCached = null;
        }
    }

    /**
     * Returns the first n lines from the log file as a list of strings.
     * If n is greater than the number of available lines, all lines are returned.
     *
     * @param n number of lines to return
     * @return list of first n lines
     */
    public synchronized List<String> getFirstNLines(int n) {
        List<String> allLines = getAllLinesList();
        if (allLines.isEmpty()) {
            return new ArrayList<>();
        }

        int endIndex = Math.min(allLines.size(), Math.max(0, n));
        return new ArrayList<>(allLines.subList(0, endIndex));
    }

    /**
     * Returns the first line from the log file.
     * If the log file is empty, returns an empty string.
     *
     * @return the first line or empty string if file is empty
     */
    public synchronized String getFirstLine() {
        List<String> firstLine = getFirstNLines(1);
        return !firstLine.isEmpty() ? firstLine.get(0) : "";
    }

    /**
     * Returns the last n lines from the log file as a list of strings.
     * If n is greater than the number of available lines, all lines are returned.
     *
     * @param n number of lines to return
     * @return list of last n lines
     */
    public synchronized List<String> getLastNLines(int n) {
        List<String> allLines = getAllLinesList();
        if (allLines.isEmpty()) {
            return new ArrayList<>();
        }

        int startIndex = Math.max(0, allLines.size() - n);
        return new ArrayList<>(allLines.subList(startIndex, allLines.size()));
    }

    /**
     * Returns the last line from the log file.
     * If the log file is empty, returns an empty string.
     *
     * @return the last line or empty string if file is empty
     */
    public synchronized String getLastLine() {
        List<String> lastLine = getLastNLines(1);
        return !lastLine.isEmpty() ? lastLine.get(0) : "";
    }

    public int getCountedLines() {
        return countedLines;
    }

    public boolean isLineCountInterrupted() {
        return lineCountInterrupted;
    }

    public boolean isContentTransformed() {
        return contentTransformed;
    }

    public long getProcessedLength() {
        return processedLength;
    }

    public boolean isProcessedLengthComplete() {
        return processedLengthComplete;
    }
}
