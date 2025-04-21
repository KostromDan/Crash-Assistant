package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.util.List;
import java.util.Optional;

public class LogAnalysisUtils {
    static Optional<String> problematicFrame = null;

    public static synchronized Optional<String> getProblematicFrameString(Log log) {
        ifBlock:
        if (problematicFrame == null) {
            if (log.getType() != LogType.HS_ERR) {
                problematicFrame = Optional.empty();
                break ifBlock;
            }
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
            problematicFrame = Optional.empty();
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
