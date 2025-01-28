package dev.kostromdan.mods.crash_assistant.mod_list;

import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModListDiff {
    private final LinkedHashSet<Mod> addedMods;
    private final LinkedHashSet<Mod> removedMods;
    private final LinkedHashSet<UpdatedPair> updatedMods;

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
        HashMap<String, HashSet<Mod>> modIdToMods = new HashMap<>();

        HashSet<Mod> tmpMods = new HashSet<>();
        tmpMods.addAll(removedMods);
        tmpMods.addAll(addedMods);
        for (Mod mod : tmpMods) {
            if (mod.getModId() != null) {
                modIdToMods.computeIfAbsent(mod.getModId(), k -> new HashSet<>()).add(mod);
            }
        }

        for (Mod mod : (LinkedHashSet<Mod>) removedMods.clone()) {
            if (mod.getModId() != null) {
                HashSet<Mod> modsWithSameModId = modIdToMods.get(mod.getModId());
                modsWithSameModId.remove(mod);
                for (Mod updatedMod : modsWithSameModId) {
                    updatedMods.add(new UpdatedPair(mod, updatedMod));
                    addedMods.remove(updatedMod);
                    removedMods.remove(mod);
                }
            }
        }
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

    public ModListDiffStringBuilder generateDiffMsg(boolean forMsg) {
        Function<String, String> langFunc = forMsg ? LanguageProvider::getMsgLang : LanguageProvider::get;
        ModListDiffStringBuilder sb = new ModListDiffStringBuilder();
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {

            List<String> modpackCreators = CrashAssistantConfig.getModpackCreators();
            if (modpackCreators.contains(ModListUtils.getCurrentUsername()) || modpackCreators.isEmpty()) {
                sb.append(langFunc.apply("msg.modlist_changes_latest_launch"));
                if (ModListUtils.getSavedModList().isEmpty() && CrashAssistantConfig.getBoolean("modpack_modlist.auto_update")) {
                    sb.append(langFunc.apply("msg.modlist_first_launch"), "blue");
                    return sb;
                }
            } else {
                sb.append(langFunc.apply("msg.modlist_changes_modpack"));
            }

            if (isEmpty()) {
                sb.append(langFunc.apply("msg.modlist_unmodified"), "blue");
            } else {
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
            }
        }
        return sb;
    }

}