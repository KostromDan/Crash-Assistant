package dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser;

import dev.kostromdan.mods.crash_assistant.app.gui.ControlPanel;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HsErrParser {
    private static final java.util.Map<Log, HsErrParsingResult> parsingResultCache = new WeakHashMap<>();


    public static synchronized Optional<HsErrParsingResult> parseHsErr(Log log) {
        if (log.getType() != LogType.HS_ERR) {
            return Optional.empty();
        }
        if (!parsingResultCache.containsKey(log)) {
            HsErrParsingResult result = new HsErrParsingResult();
            boolean isInsufficientMemory = false;
            List<String> lines = log.getReader().getAllLinesList();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("# Problematic frame:")) {
                    String fullString = line;
                    if (i + 1 < lines.size()) {
                        String frame = lines.get(i + 1);
                        fullString += "\n" + frame;
                        if (frame.trim().equals("#") && i + 2 < lines.size() && lines.get(i + 2).contains("error occurred during error reporting")) {
                            frame = lines.get(i + 2);
                            fullString += "\n" + frame;
                        }
                        result.setProblematicFrame(frame);
                        result.setProblematicFrameFullString(fullString);
                        break;
                    }
                } else if (line.contains("# There is insufficient memory for the Java Runtime Environment to continue.")) {
                    result.setProblematicFrame(line);
                    result.setProblematicFrameFullString(line);
                    isInsufficientMemory = true;
                    break;
                }
            }
            parseMemorySettings(log, result, isInsufficientMemory);
            parsingResultCache.put(log, result);
        }
        return Optional.of(parsingResultCache.get(log));
    }

    public static synchronized Optional<HsErrParsingResult> getCachedHsErrParsingResult() {
        return parsingResultCache.entrySet().stream()
                .filter(entry -> entry.getKey().getType() == LogType.HS_ERR)
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static void parseMemorySettings(Log log, HsErrParsingResult parsingResult, boolean isInsufficientMemory) {
        String allLines = log.getReader().getAllLinesString();
        Pattern pattern = Pattern.compile("(Memory:[^\\n]*physical (\\d+)M[^\\n]*)\\n(TotalPageFile size (\\d+)M[^\\n]*)");
        Matcher matcher = pattern.matcher(allLines);

        if (!matcher.find()) return;

        String memoryLine = matcher.group(1);
        String pageFileLine = matcher.group(3);
        String completeLines = memoryLine + "\n" + pageFileLine;
        if (isInsufficientMemory) {
            parsingResult.appendProblematicFrameFullString("\n...\n" + completeLines);
            parsingResult.appendProblematicFrameFullString("\n...\n" + ControlPanel.getCurrentMemoryAgsMessage());
        }

        int physicalMemory = Integer.parseInt(matcher.group(2));
        int pageFileSize = Integer.parseInt(matcher.group(4));

        parsingResult.setPhysicalMemory(physicalMemory);
        parsingResult.setPageFileSize(pageFileSize);
    }

    public static int countMatchedFrames(Log log, String... frames) {
        Optional<HsErrParsingResult> parsingResult = parseHsErr(log);
        if (!parsingResult.isPresent()) {
            return 0;
        }
        Optional<String> problematicFrame = parsingResult.get().getProblematicFrame();
        if (!problematicFrame.isPresent()) {
            return 0;
        }
        int count = 0;
        for (String frame : frames) {
            if (problematicFrame.get().contains(frame)) {
                count++;
            }
        }
        return count;
    }

    public static boolean hsErrContainsOneOfFrames(Log log, String... frames) {
        return countMatchedFrames(log, frames) > 0;
    }

    public static boolean hsErrContainsAllOfFrames(Log log, String... frames) {
        return countMatchedFrames(log, frames) == frames.length;
    }
}
