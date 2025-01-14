package dev.kostromdan.mods.crash_assistant.app.utils;

import org.apache.commons.io.input.ReversedLinesFileReader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LogProcessor {
    final static int maxUploadLines = 25000;
    final static int maxUploadLength = 10485760;
    int countedLines = 0;
    boolean lineCountInterrupted = false;
    List<String> firstLines;
    List<String> lastLines;
    Path logPath;

    public LogProcessor(Path logPath) {
        this.firstLines = new ArrayList<>(maxUploadLines);
        this.lastLines = null;
        this.logPath = logPath;
    }

    public void processLogFile() throws IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(this.logPath.toFile()))) {
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
                return;
            } else {
                long timeCountStarted = Instant.now().toEpochMilli();
                while (reader.readLine() != null) {
                    countedLines++;
                    if(countedLines % 100 == 0 && Instant.now().toEpochMilli() - timeCountStarted > 1000){
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
    }

    /**
     * Different versions of common-io having different implementations, so we have to deal with it.
     */
    private ReversedLinesFileReader createReversedLinesFileReader() throws IOException {
        try {
            return ReversedLinesFileReader.builder()
                    .setPath(this.logPath)
                    .setCharset(StandardCharsets.UTF_8)
                    .get();
        } catch (NoSuchMethodError e) {
            return new ReversedLinesFileReader(logPath.toFile());
        }
    }


    public String getFirstLinesString() {
        return String.join("\n", firstLines);

    }

    public String getLastLinesString() {
        return lastLines == null ? null : String.join("\n", lastLines);
    }

    public int getCountedLines() {
        return countedLines;
    }

    public boolean isLineCountInterrupted() {
        return lineCountInterrupted;
    }
}