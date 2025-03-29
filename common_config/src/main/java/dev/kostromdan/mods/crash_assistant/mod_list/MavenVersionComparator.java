package dev.kostromdan.mods.crash_assistant.mod_list;

import java.util.LinkedHashSet;

public class MavenVersionComparator {
    public static void leaveOnlyOneWithHighestVersion(LinkedHashSet<Mod> mods) {
        if (mods.isEmpty()) {
            return;
        }

        Mod highest = null;
        for (Mod mod : mods) {
            if (highest == null) {
                highest = mod;
            } else {
                int versionCompare = new ComparableVersion(mod.getVersion())
                        .compareTo(new ComparableVersion(highest.getVersion()));
                if (versionCompare > 0 ||
                        (versionCompare == 0 && mod.getJarName().compareTo(highest.getJarName()) > 0)) {
                    highest = mod;
                }
            }
        }
        mods.clear();
        mods.add(highest);
    }
}
