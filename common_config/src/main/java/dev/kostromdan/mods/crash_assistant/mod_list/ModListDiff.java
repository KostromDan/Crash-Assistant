package dev.kostromdan.mods.crash_assistant.mod_list;

import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.platform.PlatformHelp;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModListDiff {
    private final LinkedHashSet<Mod> addedMods;
    private final LinkedHashSet<Mod> removedMods;
    private final LinkedHashSet<UpdatedPair> updatedMods;
    private static String filePrefix = null;

    public ModListDiff(LinkedHashSet<Mod> saved, LinkedHashSet<Mod> current) {
        // Added mods: present in current but not in saved
        addedMods = current.stream()
                .filter(mod -> !saved.contains(mod))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // Removed mods: present in saved but not in current
        removedMods = saved.stream()
                .filter(mod -> !current.contains(mod))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        updatedMods = new LinkedHashSet<>();
        HashSet<String> updatedModsModIds = new HashSet<>();

        LinkedHashMap<String, Pair<LinkedHashSet<Mod>, LinkedHashSet<Mod>>> modIdToModMap = new LinkedHashMap<>();

        for (Mod mod : saved) {
            if (mod.getModId() == null) continue;
            modIdToModMap
                    .computeIfAbsent(mod.getModId(), k -> new Pair<>(new LinkedHashSet<>(), new LinkedHashSet<>()))
                    .getFirst()
                    .add(mod);
        }

        for (Mod mod : current) {
            if (mod.getModId() == null) continue;
            modIdToModMap
                    .computeIfAbsent(mod.getModId(), k -> new Pair<>(new LinkedHashSet<>(), new LinkedHashSet<>()))
                    .getSecond()
                    .add(mod);
        }

        for (var entry : modIdToModMap.entrySet()) {
            String modId = entry.getKey();
            var pair = entry.getValue();

            if (pair.getFirst().isEmpty() || pair.getSecond().isEmpty()) {
                continue;
            }

            if (PlatformHelp.isPlatformFabricBased()) {
                leaveOnlyOneWithHighestVersion(pair.getFirst());
                leaveOnlyOneWithHighestVersion(pair.getSecond());
            }

            if (pair.getFirst().equals(pair.getSecond())) {
                continue;
            }
            Mod currentSavedMod = pair.getFirst().getFirst();
            LinkedHashSet<Mod> currentCurrentMods = pair.getSecond();

            for (Mod mod : currentCurrentMods) {
                updatedMods.add(new UpdatedPair(currentSavedMod, mod));
                updatedModsModIds.add(modId);
            }
        }
        addedMods.removeIf(addedMod -> updatedModsModIds.contains(addedMod.getModId()));
        removedMods.removeIf(removedMod -> updatedModsModIds.contains(removedMod.getModId()));
    }

    public LinkedHashSet<Mod> getAddedMods() {
        return addedMods;
    }

    public LinkedHashSet<Mod> getRemovedMods() {
        return removedMods;
    }

    public LinkedHashSet<UpdatedPair> getUpdatedMods() {
        return updatedMods;
    }

    public static ModListDiff getDiff() {
        return new ModListDiff(ModListUtils.getSavedModList(), ModListUtils.getCurrentModList());
    }

    public boolean isEmpty() {
        return addedMods.isEmpty() && removedMods.isEmpty() && updatedMods.isEmpty();
    }

    public static boolean isModpackCreator() {
        List<String> modpackCreators = CrashAssistantConfig.getModpackCreators();
        return modpackCreators.contains(ModListUtils.getCurrentUsername()) || modpackCreators.isEmpty();
    }

    public static String getFirstString(boolean forMsg, boolean isMd, String link) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        StringBuilder sb = new StringBuilder();
        String secondPart;
        if (isMd) sb.append("[");
        if (ModListDiff.isModpackCreator()) {
            sb.append(langFunc.apply("msg.modlist_changes_latest_launch_1"));
            secondPart = langFunc.apply("msg.modlist_changes_latest_launch_2");
        } else {
            sb.append(langFunc.apply("msg.modlist_changes_modpack_1"));
            secondPart = langFunc.apply("msg.modlist_changes_modpack_2");
        }
        if (isMd) {
            sb.append("]").append("(<").append(link).append(">)");
        }
        sb.append(secondPart);
        return sb.toString();
    }

    public ModListDiffStringBuilder generateDiffMsg(boolean forMsg) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        ModListDiffStringBuilder sb = new ModListDiffStringBuilder();
        if (!CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) return sb;

        sb.append(getFirstString(forMsg, false, null));
        if (isModpackCreator()) {
            if (ModListUtils.getSavedModList().isEmpty() && CrashAssistantConfig.getBoolean("modpack_modlist.auto_update")) {
                sb.append(langFunc.apply("msg.modlist_first_launch"), "blue");
                return sb;
            }
        }

        if (isEmpty()) {
            sb.append(langFunc.apply("msg.modlist_unmodified"), "blue");
            return sb;
        }
        if (!getAddedMods().isEmpty()) {
            sb.append(langFunc.apply("msg.added_mods"));
            for (Mod mod : getAddedMods()) {
                sb.append(mod.getJarName(), "green");
            }
            sb.append("");
        }
        if (!getRemovedMods().isEmpty()) {
            sb.append(langFunc.apply("msg.removed_mods"));
            for (Mod mod : getRemovedMods()) {
                sb.append(mod.getJarName(), "red");
            }
            sb.append("");
        }
        if (!getUpdatedMods().isEmpty()) {
            sb.append(langFunc.apply("msg.updated_mods"));
            for (UpdatedPair updatedPair : getUpdatedMods()) {
                sb.append(updatedPair.getOldMod().getModId(), "blue", false);
                sb.append(" (", false);
                sb.append(updatedPair.getOldMod().getVersion(), "red", false);
                sb.append(" > ", false);
                sb.append(updatedPair.getNewMod().getVersion(), "green", false);
                sb.append(")");
                if (Objects.equals(updatedPair.getOldMod().getVersion(), updatedPair.getNewMod().getVersion())) {
                    sb.append(updatedPair.getOldMod().getJarName(), "green", false);
                    sb.append(" > ", false);
                    sb.append(updatedPair.getNewMod().getJarName(), "red");
                }
            }
        }

        return sb;
    }

    public static String getFilePrefix() {
        if (filePrefix == null) {
            if (CrashAssistantConfig.getBoolean("generated_message.h3_prefix")) {
                filePrefix = "### ";
            } else {
                filePrefix = "";
            }
        }
        return filePrefix;
    }

    class Pair<F, S> {
        private final F first;
        private final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }
    }
}