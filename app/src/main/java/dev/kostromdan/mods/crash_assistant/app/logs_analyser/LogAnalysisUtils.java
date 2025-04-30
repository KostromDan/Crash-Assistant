package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.util.List;
import java.util.Optional;

public class LogAnalysisUtils {
    public static boolean analysedForProblematicFrame = false;
    public static Optional<String> problematicFrame = Optional.empty();

    public static synchronized Optional<String> getProblematicFrameString(Log log) {
        ifBlock:
        if (!analysedForProblematicFrame) {
            if (log.getType() != LogType.HS_ERR) {
                break ifBlock;
            }
            analysedForProblematicFrame = true;
            List<String> lines = log.getReader().getAllLinesList();
            boolean found = false;
            for (String line : lines) {
                if (found) {
                    problematicFrame = Optional.of(line);
                    break ifBlock;
                }
                if (line.contains("# Problematic frame:")) {
                    found = true;
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
