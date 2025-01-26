package dev.kostromdan.mods.crash_assistant.mod_list;

import java.nio.file.Path;
import java.util.Comparator;

public class PathComparator implements Comparator<Path> {
    @Override
    public int compare(Path p1, Path p2) {
        return String.CASE_INSENSITIVE_ORDER.compare(p1.getFileName().toString(), p2.getFileName().toString());
    }
}
