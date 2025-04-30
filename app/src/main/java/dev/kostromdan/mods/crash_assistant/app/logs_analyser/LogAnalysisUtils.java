package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.util.List;
import java.util.Optional;

public class LogAnalysisUtils {
    public static boolean analysedForProblematicFrame = false;
    public static Optional<String> problematicFrame = Optional.empty();
    public static Optional<String> problematicFrameFullString = Optional.empty();

    public static synchronized Optional<String> getProblematicFrameString(Log log) {
        ifBlock:
        if (!analysedForProblematicFrame) {
            if (log.getType() != LogType.HS_ERR) {
                break ifBlock;
            }
            analysedForProblematicFrame = true;
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
                        problematicFrame = Optional.of(frame);
                        problematicFrameFullString = Optional.of(fullString);
                    }
                    break ifBlock;
                } else if (line.contains("# There is insufficient memory for the Java Runtime Environment to continue.")) {
                    problematicFrame = Optional.of(line);
                    problematicFrameFullString = Optional.of(line);
                    break ifBlock;
                }
            }
        }
        return problematicFrame;
    }

    public static int countMatchedFrames(Log log, String... frames) {
        Optional<String> problematicFrame = getProblematicFrameString(log);
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
