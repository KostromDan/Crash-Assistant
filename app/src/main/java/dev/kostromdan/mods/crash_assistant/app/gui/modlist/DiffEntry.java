package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.api.CurseForge;
import dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.api.Modrinth;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.UpdatedPair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class DiffEntry {
    final ModListDiffDialog.SectionType type;
    final List<ModInstance> currentMods;
    final List<ModInstance> savedMods;
    final boolean messyPair;
    final String tooltip;
    boolean selected = true;
    boolean resolved = false;
    boolean removedByAction = false;
    ModListDiffDialog.SectionAction resolvedBy = null;
    ModListDiffDialog.ActionState revertState = ModListDiffDialog.ActionState.IDLE;
    ModListDiffDialog.ActionState restoreState = ModListDiffDialog.ActionState.IDLE;
    final boolean modloaderEntry;
    final boolean actionableModFileEntry;

    DiffEntry(ModListDiffDialog.SectionType type, Mod currentMod, Mod savedMod) {
        this.type = type;
        this.currentMods = currentMod == null ? Collections.<ModInstance>emptyList() : Collections.singletonList(ModInstance.fromMod(currentMod));
        this.savedMods = savedMod == null ? Collections.<ModInstance>emptyList() : Collections.singletonList(ModInstance.fromMod(savedMod));
        this.messyPair = currentMod != null && currentMod.isModMessedUpWithVersion();
        this.tooltip = buildTooltip(currentMods, savedMods);
        this.modloaderEntry = detectModloader(currentMods, savedMods);
        this.actionableModFileEntry = detectActionableModFile(currentMods, savedMods);
        this.selected = actionableModFileEntry;
    }

    DiffEntry(UpdatedPair pair) {
        this.type = ModListDiffDialog.SectionType.UPDATED;
        this.currentMods = toInstances(pair.getNewMods());
        this.savedMods = toInstances(pair.getOldMods());
        this.messyPair = computeMessy(pair);
        this.tooltip = buildTooltip(currentMods, savedMods);
        this.modloaderEntry = detectModloader(currentMods, savedMods);
        this.actionableModFileEntry = detectActionableModFile(currentMods, savedMods);
        this.selected = actionableModFileEntry;
    }

    private List<ModInstance> toInstances(Iterable<Mod> mods) {
        List<ModInstance> list = new ArrayList<ModInstance>();
        if (mods == null) return list;
        for (Mod mod : mods) {
            if (mod != null) {
                list.add(ModInstance.fromMod(mod));
            }
        }
        return list;
    }

    private boolean computeMessy(UpdatedPair pair) {
        if (pair != null && pair.isAnyModMessedUpWithVersion()) {
            return true;
        }
        return currentMods.stream().anyMatch(ModInstance::isMessy) || savedMods.stream().anyMatch(ModInstance::isMessy);
    }

    private String buildTooltip(List<ModInstance> current, List<ModInstance> saved) {
        ModInstance oldMod = saved.isEmpty() ? null : saved.get(0);
        ModInstance newMod = current.isEmpty() ? null : current.get(0);
        if (oldMod != null && newMod != null) {
            return oldMod.fileName() + " \u2192 " + newMod.fileName();
        }
        return null;
    }

    private boolean detectModloader(List<ModInstance> current, List<ModInstance> saved) {
        for (ModInstance mi : current) {
            if (isModloader(mi.fileName())) return true;
        }
        for (ModInstance mi : saved) {
            if (isModloader(mi.fileName())) return true;
        }
        return false;
    }

    private boolean isModloader(String name) {
        String dn = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return dn.endsWith("(modloader)");
    }

    private boolean detectActionableModFile(List<ModInstance> current, List<ModInstance> saved) {
        for (ModInstance mod : current) {
            if (mod.path == null || isSyntheticNonModEntry(mod.fileName())) return false;
        }
        for (ModInstance mod : saved) {
            if (mod.path == null || isSyntheticNonModEntry(mod.fileName())) return false;
        }
        return !current.isEmpty() || !saved.isEmpty();
    }

    private boolean isSyntheticNonModEntry(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.endsWith("(modloader)")
                || normalized.endsWith("(resourcepack)")
                || normalized.endsWith("(datapack)");
    }

    boolean isMessyForDisplay() {
        return messyPair;
    }

    List<String> currentDisplayLines() {
        boolean messy = isMessyForDisplay();
        List<String> lines = new ArrayList<String>();
        for (ModInstance mi : currentMods) {
            String baseName = mi.fileName();
            if (!messy && mi.mod != null && mi.mod.isModMessedUpWithVersion() && hasText(mi.mod.getVersion())) {
                baseName = baseName + " (" + mi.mod.getVersion() + ")";
            }
            lines.add(baseName);
        }
        if (lines.isEmpty()) {
            lines.add("-");
        }
        return lines;
    }

    List<String> savedDisplayLines() {
        boolean messy = isMessyForDisplay() || type == ModListDiffDialog.SectionType.REMOVED;
        List<String> lines = new ArrayList<String>();
        for (ModInstance mi : savedMods) {
            if (messy) {
                lines.add(mi.fileName());
            } else {
                String v = mi.mod != null ? mi.mod.getVersion() : null;
                if (hasText(v)) {
                    lines.add(v);
                } else {
                    lines.add(mi.fileName());
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add("-");
        }
        return lines;
    }

    int maxDisplayLines() {
        if (type == ModListDiffDialog.SectionType.UPDATED) {
            return Math.max(currentDisplayLines().size(), savedDisplayLines().size());
        }
        if (type == ModListDiffDialog.SectionType.REMOVED) {
            return Math.max(1, savedDisplayLines().size());
        }
        return Math.max(1, currentDisplayLines().size());
    }

    List<String> primaryDisplayLines() {
        if (type == ModListDiffDialog.SectionType.REMOVED && !savedMods.isEmpty()) {
            return savedDisplayLines();
        }
        if (type == ModListDiffDialog.SectionType.UPDATED && currentMods.isEmpty() && !savedMods.isEmpty()) {
            return savedDisplayLines();
        }
        return currentDisplayLines();
    }

    String primaryDisplayText() {
        return toMultiline(primaryDisplayLines());
    }

    String savedDisplayText() {
        return toMultiline(savedDisplayLines());
    }

    private String toMultiline(List<String> lines) {
        if (lines.isEmpty()) return "-";
        if (lines.size() == 1) return escape(lines.get(0));
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < lines.size(); i++) {
            sb.append(escape(lines.get(i)));
            if (i < lines.size() - 1) sb.append("<br>");
        }
        sb.append("</html>");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }

    boolean hasAnyCurseMatch() {
        for (ModInstance mi : currentMods) {
            if (mi.curseMatch != null) return true;
        }
        for (ModInstance mi : savedMods) {
            if (mi.curseMatch != null) return true;
        }
        return false;
    }

    boolean hasAnyModrinthMatch() {
        for (ModInstance mi : currentMods) {
            if (mi.modrinthMatch != null) return true;
        }
        for (ModInstance mi : savedMods) {
            if (mi.modrinthMatch != null) return true;
        }
        return false;
    }

    Set<Long> getCurseHashes(boolean includeSaved) {
        Set<Long> set = new HashSet<Long>();
        for (ModInstance mi : currentMods) {
            if (mi.curseHash != null) set.add(mi.curseHash);
        }
        if (includeSaved) {
            for (ModInstance mi : savedMods) {
                if (mi.curseHash != null) set.add(mi.curseHash);
            }
        }
        return set;
    }

    Set<String> getModrinthHashes(boolean includeSaved) {
        Set<String> set = new HashSet<String>();
        for (ModInstance mi : currentMods) {
            if (mi.modrinthHash != null) set.add(mi.modrinthHash);
        }
        if (includeSaved) {
            for (ModInstance mi : savedMods) {
                if (mi.modrinthHash != null) set.add(mi.modrinthHash);
            }
        }
        return set;
    }

    ModInstance anyCurseMatchInstance() {
        for (ModInstance mi : currentMods) {
            if (mi.curseMatch != null) return mi;
        }
        for (ModInstance mi : savedMods) {
            if (mi.curseMatch != null) return mi;
        }
        return null;
    }

    ModInstance anyModrinthMatchInstance() {
        for (ModInstance mi : currentMods) {
            if (mi.modrinthMatch != null) return mi;
        }
        for (ModInstance mi : savedMods) {
            if (mi.modrinthMatch != null) return mi;
        }
        return null;
    }

    List<Path> currentPaths() {
        List<Path> list = new ArrayList<Path>();
        for (ModInstance mi : currentMods) {
            if (mi.path != null) list.add(mi.path);
        }
        return list;
    }

    List<Path> savedPaths() {
        List<Path> list = new ArrayList<Path>();
        for (ModInstance mi : savedMods) {
            if (mi.path != null) list.add(mi.path);
        }
        return list;
    }

    boolean hasAnyCurrentPath() {
        return !currentPaths().isEmpty();
    }

    boolean areAllCurrentDisabled() {
        List<Path> paths = currentPaths();
        if (paths.isEmpty()) return false;
        for (Path p : paths) {
            if (p == null) continue;
            if (!p.getFileName().toString().endsWith(".disabled")) return false;
        }
        return true;
    }

    static class ModInstance {
        final Mod mod;
        Path path;
        final Long curseHash;
        final String modrinthHash;
        CurseForge.FingerprintMatch curseMatch;
        Modrinth.VersionFileInfo modrinthMatch;

        static ModInstance fromMod(Mod mod) {
            Path directPath = null;
            if (mod != null) {
                try {
                    directPath = ModListUtils.resolveDirectModFile(mod.getJarName());
                } catch (IllegalArgumentException ignored) {
                    // Invalid/cross-platform path syntax may still be displayed in an
                    // old or damaged snapshot, but it must never become actionable.
                }
            }
            return new ModInstance(mod,
                    directPath,
                    mod != null ? mod.getCurseForgeHash() : null,
                    normalizeHash(mod != null ? mod.getModrinthHash() : null));
        }

        ModInstance(Mod mod, Path path, Long curseHash, String modrinthHash) {
            this.mod = mod;
            this.path = path;
            this.curseHash = curseHash;
            this.modrinthHash = modrinthHash;
        }

        String fileName() {
            if (path != null && path.getFileName() != null) {
                return path.getFileName().toString();
            }
            if (mod != null && mod.getJarName() != null) {
                return mod.getJarName();
            }
            return "unknown";
        }

        boolean isMessy() {
            return mod != null && mod.isModMessedUpWithVersion();
        }

        private static String normalizeHash(String hash) {
            return hash == null ? null : hash.toLowerCase(Locale.ROOT);
        }
    }
}
