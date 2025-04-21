package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import org.apache.commons.io.input.ReversedLinesFileReader;

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
    Path logPath;
    boolean isLogProcessed = false;
    long sizeOnLastRead = -1;

    public LogReader(Path logPath) {
        this.firstLines = new ArrayList<>(maxUploadLines);
        this.lastLines = null;
        this.logPath = logPath;
    }

    public synchronized void readLogFile(boolean checkUpdated) throws IOException {
        if (isLogProcessed && (!checkUpdated || Files.size(logPath) == sizeOnLastRead)) {
            return;
        }
        sizeOnLastRead = Files.size(logPath);
        countedLines = 0;
        lineCountInterrupted = false;
        lastLines = null;
        firstLines = new ArrayList<>(maxUploadLines);


        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.logPath.toFile()), StandardCharsets.UTF_8))) {
            String line;
            int length = 0;

            while ((line = reader.readLine()) != null && countedLines < maxUploadLines && length < maxUploadLength) {
                if (line.isEmpty()) {
                    continue;
                }
                firstLines.add(line);
                length += line.length() + 1;
                countedLines++;
            }
            if (line == null) {
                isLogProcessed = true;
                return;
            } else {
                long timeCountStarted = Instant.now().toEpochMilli();
                while (reader.readLine() != null) {
                    countedLines++;
                    if (countedLines % 100 == 0 && Instant.now().toEpochMilli() - timeCountStarted >= 1000) {
                        lineCountInterrupted = true;
                        break;
                    }
                }
            }
        }
        lastLines = new LinkedList<>();
        try (ReversedLinesFileReader reversedReader = createReversedLinesFileReader()) {
            String line;
            int count = 0;
            int length = 0;
            while ((line = reversedReader.readLine()) != null && count < maxUploadLines && length < maxUploadLength) {
                if (line.isEmpty()) {
                    continue;
                }
                lastLines.add(0, line);
                length += line.length() + 1;
                count++;
            }
            if (length > maxUploadLength) {
                lastLines.remove(0);
            }
        }
        isLogProcessed = true;
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
    private ReversedLinesFileReader createReversedLinesFileReader() throws IOException {
        try {
            return ReversedLinesFileReader.builder()
                    .setPath(this.logPath)
                    .setCharset(StandardCharsets.UTF_8)
                    .setBufferSize(1024 * 1024)
                    .get();
        } catch (NoSuchMethodError e) {
            return new ReversedLinesFileReader(logPath.toFile(), 1024 * 1024, StandardCharsets.UTF_8);
        }
    }


    public String getFirstLinesString() {
        return String.join("\n", firstLines);

    }

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

    public synchronized void destroyAllLinesCache() {
        synchronized (this) {
            allLinesStringCached = null;
            allLinesListCached = null;
        }
    }

    public int getCountedLines() {
        return countedLines;
    }

    public boolean isLineCountInterrupted() {
        return lineCountInterrupted;
    }
}