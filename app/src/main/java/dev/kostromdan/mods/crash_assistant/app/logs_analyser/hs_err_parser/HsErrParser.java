package dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;

import java.util.List;
import java.util.Optional;

public class HsErrParser {
    private static HsErrParsingResult parsingResultCache = null;


    public static synchronized Optional<HsErrParsingResult> parseHsErr(Log log) {
        if (log.getType() != LogType.HS_ERR) {
            return Optional.empty();
        }
        if (parsingResultCache == null) {
            parsingResultCache = new HsErrParsingResult();
            boolean isInsufficientMemory = false;
            List<String> lines = log.getReader().getAllLinesList();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("# Problematic frame:")) {
                    String fullString = line;
                    if (i + 1 < lines.size()) {
                        String frame = lines.get(i + 1);
                        fullString += "\n" + frame;
                        if (frame.strip().equals("#") && i + 2 < lines.size() && lines.get(i + 2).contains("error occurred during error reporting")) {
                            frame = lines.get(i + 2);
                            fullString += "\n" + frame;
                        }
                        parsingResultCache.setProblematicFrame(frame);
                        parsingResultCache.setProblematicFrameFullString(fullString);
                        break;
                    }
                } else if (line.contains("# There is insufficient memory for the Java Runtime Environment to continue.")) {
                    parsingResultCache.setProblematicFrame(line);
                    parsingResultCache.setProblematicFrameFullString(line);
                    isInsufficientMemory = true;
                    break;
                }
            }
            parseMemorySettings(log, parsingResultCache, isInsufficientMemory);
        }
        return Optional.of(parsingResultCache);
    }

    public static synchronized Optional<HsErrParsingResult> getCachedHsErrParsingResult() {
        return Optional.ofNullable(parsingResultCache);
    }

    private static void parseMemorySettings(Log log, HsErrParsingResult parsingResult, boolean isInsufficientMemory) {
        String allLines = log.getReader().getAllLinesString();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(Memory:[^\\n]*physical (\\d+)M[^\\n]*)\\n(TotalPageFile size (\\d+)M[^\\n]*)");
        java.util.regex.Matcher matcher = pattern.matcher(allLines);

        if (!matcher.find()) return;

        String memoryLine = matcher.group(1);
        String pageFileLine = matcher.group(3);
        String completeLines = memoryLine + "\n" + pageFileLine;
        if (isInsufficientMemory) {
            parsingResult.appendProblematicFrameFullString("\n...\n" + completeLines);
        }

        int physicalMemory = Integer.parseInt(matcher.group(2));
        int pageFileSize = Integer.parseInt(matcher.group(4));

        parsingResult.setPhysicalMemory(physicalMemory);
        parsingResult.setPageFileSize(pageFileSize);
    }

    public static int countMatchedFrames(Log log, String... frames) {
        Optional<HsErrParsingResult> parsingResult = parseHsErr(log);
        if (parsingResult.isEmpty()) {
            return 0;
        }
        Optional<String> problematicFrame = parsingResult.get().getProblematicFrame();
        if (problematicFrame.isEmpty()) {
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
