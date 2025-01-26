package dev.kostromdan.mods.crash_assistant.mod_list;

public class UpdatedPair {
    private final Mod oldMod;
    private final Mod newMod;

    public UpdatedPair(Mod oldMod, Mod newMod) {
        this.oldMod = oldMod;
        this.newMod = newMod;
    }

    public Mod getOldMod() {
        return oldMod;
    }

    public Mod getNewMod() {
        return newMod;
    }
}
