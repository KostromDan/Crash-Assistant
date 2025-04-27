package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModListDiff {
    private final LinkedHashSet<Mod> currentMods;
    private final LinkedHashSet<Mod> addedMods;
    private final LinkedHashSet<Mod> removedMods;
    private final LinkedHashSet<UpdatedPair> updatedMods;
    private static String filePrefix = null;

    public ModListDiff(LinkedHashSet<Mod> saved, LinkedHashSet<Mod> current) {
        currentMods = current;

        // Added mods: present in current but not in saved
        addedMods = current.stream()
                .filter(mod -> !saved.contains(mod))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // Removed mods: present in saved but not in current
        removedMods = saved.stream()
                .filter(mod -> !current.contains(mod))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        updatedMods = new LinkedHashSet<>();

        LinkedHashMap<String, UpdatedPair> updatedPairCandidates = new LinkedHashMap<>();

        for (Mod mod : saved) {
            if (mod.getModId() == null) continue;
            updatedPairCandidates
                    .computeIfAbsent(mod.getModId(), k -> new UpdatedPair(new LinkedHashSet<>(), new LinkedHashSet<>()))
                    .getOldMods()
                    .add(mod);
        }

        for (Mod mod : current) {
            if (mod.getModId() == null) continue;
            updatedPairCandidates
                    .computeIfAbsent(mod.getModId(), k -> new UpdatedPair(new LinkedHashSet<>(), new LinkedHashSet<>()))
                    .getNewMods()
                    .add(mod);
        }

        var iterator = updatedPairCandidates.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var pair = entry.getValue();

            if (pair.getOldMods().isEmpty() || pair.getNewMods().isEmpty() ||
                    pair.getOldMods().equals(pair.getNewMods())) {
                iterator.remove();
            }
        }
        updatedMods.addAll(updatedPairCandidates.values());
        addedMods.removeIf(addedMod -> updatedPairCandidates.containsKey(addedMod.getModId()));
        removedMods.removeIf(removedMod -> updatedPairCandidates.containsKey(removedMod.getModId()));
    }

    public LinkedHashSet<Mod> getCurrentMods() {
        return currentMods;
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

    public static synchronized ModListDiff getDiff(boolean useCache) {
        return new ModListDiff(ModListUtils.getSavedModList(), ModListUtils.getCurrentModList(useCache));
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
                if (!updatedPair.isAnyModMessedUpWithVersion()) {
                    sb.append(updatedPair.getModId(), "blue", false);
                    sb.append(" (", false);

                    appendModAttributes(sb, updatedPair.getOldMods(), Mod::getVersion, "red");
                    sb.append(" > ", false);
                    appendModAttributes(sb, updatedPair.getNewMods(), Mod::getVersion, "green");

                    sb.append(")");
                } else {
                    appendModAttributes(sb, updatedPair.getOldMods(), Mod::getJarName, "red");
                    sb.append(" > ", false);
                    appendModAttributes(sb, updatedPair.getNewMods(), Mod::getJarName, "green");
                    sb.append("");
                }

            }
        }
        return sb;
    }

    private void appendModAttributes(ModListDiffStringBuilder sb, Collection<Mod> mods, Function<Mod, String> attributeExtractor, String color) {
        if (mods.size() == 1) {
            sb.append(attributeExtractor.apply(mods.iterator().next()), color, false);
        } else {
            sb.append("(", false);
            Iterator<Mod> iterator = mods.iterator();
            while (iterator.hasNext()) {
                Mod mod = iterator.next();
                sb.append(attributeExtractor.apply(mod), color, false);
                if (iterator.hasNext()) {
                    sb.append(", ", false);
                }
            }
            sb.append(")", false);
        }
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
}