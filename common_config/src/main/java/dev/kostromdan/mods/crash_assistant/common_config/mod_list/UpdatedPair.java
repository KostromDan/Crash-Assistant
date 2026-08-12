package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;

public class UpdatedPair {
    private final LinkedHashSet<Mod> oldMods;
    private final LinkedHashSet<Mod> newMods;
    private final LinkedHashSet<Mod> mismatchedHashNewMods = new LinkedHashSet<>();
    private boolean modMessedUpWithVersion;

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

    public boolean oldModsEqualsNewMods() {
        if (!mismatchedHashNewMods.isEmpty()) {
            return false;
        }
        if (oldMods.equals(newMods)) {
            return true;
        }
        if (oldMods.size() == newMods.size() && oldMods.size() == 1) {
            return oldMods.iterator().next().getJarName().equalsIgnoreCase(newMods.iterator().next().getJarName());
        }
        return false;
    }

    void markMismatchedHash(Mod newMod) {
        mismatchedHashNewMods.add(newMod);
        modMessedUpWithVersion = true;
    }

    public boolean hasMismatchedHash(Mod newMod) {
        return mismatchedHashNewMods.contains(newMod);
    }

    static boolean haveDifferentComparableHashes(Mod oldMod, Mod newMod) {
        if (oldMod == null || newMod == null) {
            return false;
        }

        boolean modrinthHashesDiffer = oldMod.getModrinthHash() != null &&
                newMod.getModrinthHash() != null &&
                !oldMod.getModrinthHash().equalsIgnoreCase(newMod.getModrinthHash());
        boolean curseForgeHashesDiffer = oldMod.getCurseForgeHash() != null &&
                newMod.getCurseForgeHash() != null &&
                !oldMod.getCurseForgeHash().equals(newMod.getCurseForgeHash());
        return modrinthHashesDiffer || curseForgeHashesDiffer;
    }

    public String getModId() {
        return oldMods.iterator().next().getModId();
    }

    public boolean isAnyModMessedUpWithVersion() {
        if (modMessedUpWithVersion) {
            return true;
        }
        block:
        {
            HashMap<String, HashSet<String>> versionToJarNames = new HashMap<>();

            for (Mod mod : oldMods) {
                String version = mod.getVersion();
                if (version == null || version.isEmpty()) {
                    break block;
                }
                versionToJarNames.computeIfAbsent(version, k -> new HashSet<>()).add(mod.getJarName());
            }

            for (Mod mod : newMods) {
                String version = mod.getVersion();
                if (version == null || version.isEmpty()) {
                    break block;
                }
                versionToJarNames.computeIfAbsent(version, k -> new HashSet<>()).add(mod.getJarName());
            }

            for (Map.Entry<String, HashSet<String>> entry : versionToJarNames.entrySet()) {
                HashSet<String> jarNames = entry.getValue();
                if (jarNames.size() >= 2) {
                    return true;
                }
            }
        }
        return oldMods.stream().anyMatch(Mod::isModMessedUpWithVersion) || newMods.stream().anyMatch(Mod::isModMessedUpWithVersion);
    }
}
