package dev.kostromdan.mods.crash_assistant.app.utils;

import org.apache.commons.io.input.ReversedLinesFileReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LogProcessor {

    public static void processLogFile(String filePath) throws IOException {
        int numLines = 25000;
        int maxLinesLength = 10485760;
        List<String> firstLines = new ArrayList<>(numLines);
        List<String> lastLines = new LinkedList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int count = 0;
            int length = 0;

            while ((line = reader.readLine()) != null && count < numLines && length < maxLinesLength) {
                firstLines.add(line);
                length += line.length();
                count++;
            }
        }

        try (ReversedLinesFileReader reversedReader = new ReversedLinesFileReader(
                new File(filePath), StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            int length = 0;
            while ((line = reversedReader.readLine()) != null && count < numLines && length < maxLinesLength) {
                lastLines.add(0, line);
                length += line.length();
                count++;
            }
        }
    }
}