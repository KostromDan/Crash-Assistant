package dev.kostromdan.mods.crash_assistant.mod_list;

import java.util.LinkedHashSet;

public class UpdatedPair {
    private final LinkedHashSet<Mod> oldMods;
    private final LinkedHashSet<Mod> newMods;

    public UpdatedPair(LinkedHashSet<Mod> oldMods, LinkedHashSet<Mod> newMods) {
        this.oldMods = oldMods;
        this.newMods = newMods;
    }

    public LinkedHashSet<Mod> getOldMods() {
        return oldMods;
    }

    public LinkedHashSet<Mod> getNewMods() {
        return newMods;
    }

    public boolean isOnlyOneModInEach() {
        return oldMods.size() == 1 && newMods.size() == 1;
    }
}
