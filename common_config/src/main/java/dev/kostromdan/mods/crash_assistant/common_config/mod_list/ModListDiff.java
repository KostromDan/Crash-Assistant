package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModListDiff {
    private final LinkedHashSet<Mod> savedMods;
    private final LinkedHashSet<Mod> currentMods;
    private final LinkedHashSet<Mod> addedMods;
    private final LinkedHashSet<Mod> removedMods;
    private final LinkedHashSet<UpdatedPair> updatedMods;
    private static String filePrefix = null;

    public ModListDiff(LinkedHashSet<Mod> saved, LinkedHashSet<Mod> current) {
        savedMods = saved == null ? new LinkedHashSet<Mod>() : new LinkedHashSet<Mod>(saved);
        currentMods = current == null ? new LinkedHashSet<Mod>() : new LinkedHashSet<Mod>(current);

        // Added mods: present in current but not in saved
        addedMods = currentMods.stream()
                .filter(mod -> !savedMods.contains(mod))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // Removed mods: present in saved but not in current
        removedMods = savedMods.stream()
                .filter(mod -> !currentMods.contains(mod))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        updatedMods = new LinkedHashSet<>();

        Set<Mod> hashUpdatedOldMods = Collections.newSetFromMap(new IdentityHashMap<Mod, Boolean>());
        Set<Mod> hashUpdatedNewMods = Collections.newSetFromMap(new IdentityHashMap<Mod, Boolean>());
        Map<String, HashMatchCandidates> hashCandidatesByJarName =
                new TreeMap<String, HashMatchCandidates>(String.CASE_INSENSITIVE_ORDER);
        int currentOrder = 0;
        for (Mod newMod : currentMods) {
            String jarName = newMod.getJarName();
            if (jarName != null && (newMod.getModrinthHash() != null || newMod.getCurseForgeHash() != null)) {
                hashCandidatesByJarName
                        .computeIfAbsent(jarName, ignored -> new HashMatchCandidates())
                        .add(newMod, currentOrder);
            }
            currentOrder++;
        }
        for (Mod oldMod : savedMods) {
            if (oldMod.getJarName() == null) continue;
            HashMatchCandidates candidates = hashCandidatesByJarName.get(oldMod.getJarName());
            if (candidates == null) continue;
            Mod newMod = candidates.takeFirstWithDifferentComparableHash(oldMod);
            if (newMod == null) continue;

            LinkedHashSet<Mod> oldHashMods = new LinkedHashSet<>();
            oldHashMods.add(oldMod);
            LinkedHashSet<Mod> newHashMods = new LinkedHashSet<>();
            newHashMods.add(newMod);
            UpdatedPair hashUpdatedPair = new UpdatedPair(oldHashMods, newHashMods);
            hashUpdatedPair.markModMessedUpWithVersion();
            updatedMods.add(hashUpdatedPair);
            hashUpdatedOldMods.add(oldMod);
            hashUpdatedNewMods.add(newMod);
        }

        LinkedHashMap<String, UpdatedPair> updatedPairCandidates = new LinkedHashMap<>();

        for (Mod mod : savedMods) {
            if (hashUpdatedOldMods.contains(mod) || mod.getModId() == null) continue;
            updatedPairCandidates
                    .computeIfAbsent(mod.getModId(), k -> new UpdatedPair(new LinkedHashSet<>(), new LinkedHashSet<>()))
                    .getOldMods()
                    .add(mod);
        }

        for (Mod mod : currentMods) {
            if (hashUpdatedNewMods.contains(mod) || mod.getModId() == null) continue;
            updatedPairCandidates
                    .computeIfAbsent(mod.getModId(), k -> new UpdatedPair(new LinkedHashSet<>(), new LinkedHashSet<>()))
                    .getNewMods()
                    .add(mod);
        }

        Iterator<Map.Entry<String, UpdatedPair>> iterator = updatedPairCandidates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, UpdatedPair> entry = iterator.next();
            UpdatedPair pair = entry.getValue();

            if (pair.getOldMods().isEmpty() || pair.getNewMods().isEmpty() ||
                    pair.oldModsEqualsNewMods()) {
                iterator.remove();
            }
        }
        updatedMods.addAll(updatedPairCandidates.values());
        addedMods.removeIf(addedMod -> hashUpdatedNewMods.contains(addedMod) ||
                updatedPairCandidates.containsKey(addedMod.getModId()));
        removedMods.removeIf(removedMod -> hashUpdatedOldMods.contains(removedMod) ||
                updatedPairCandidates.containsKey(removedMod.getModId()));
    }

    /**
     * Preserves the old greedy match (first still-unused current mod in current-list order),
     * while finding it through hash buckets instead of rescanning the entire current list.
     */
    private static final class HashMatchCandidates {
        private IndexedCandidate soleCandidate;
        private AttributeIndex<String> modrinthHashes;
        private AttributeIndex<Long> curseForgeHashes;
        private boolean indexed;

        private void add(Mod mod, int order) {
            IndexedCandidate candidate = new IndexedCandidate(mod, order);
            if (!indexed && soleCandidate == null) {
                soleCandidate = candidate;
                return;
            }
            if (!indexed) {
                indexed = true;
                addToIndexes(soleCandidate);
                soleCandidate = null;
            }
            addToIndexes(candidate);
        }

        private Mod takeFirstWithDifferentComparableHash(Mod oldMod) {
            if (!indexed) {
                if (soleCandidate == null
                        || !UpdatedPair.haveDifferentComparableHashes(oldMod, soleCandidate.mod)) {
                    return null;
                }
                Mod selected = soleCandidate.mod;
                soleCandidate = null;
                return selected;
            }

            IndexedCandidate modrinthCandidate = oldMod.getModrinthHash() == null || modrinthHashes == null
                    ? null
                    : modrinthHashes.firstExcluding(oldMod.getModrinthHash());
            IndexedCandidate curseForgeCandidate = oldMod.getCurseForgeHash() == null || curseForgeHashes == null
                    ? null
                    : curseForgeHashes.firstExcluding(oldMod.getCurseForgeHash());
            IndexedCandidate selected = earlier(modrinthCandidate, curseForgeCandidate);
            if (selected == null) return null;

            Mod selectedMod = selected.mod;
            if (selectedMod.getModrinthHash() != null) {
                modrinthHashes.remove(selectedMod.getModrinthHash(), selected);
            }
            if (selectedMod.getCurseForgeHash() != null) {
                curseForgeHashes.remove(selectedMod.getCurseForgeHash(), selected);
            }
            return selectedMod;
        }

        private void addToIndexes(IndexedCandidate candidate) {
            Mod mod = candidate.mod;
            if (mod.getModrinthHash() != null) {
                if (modrinthHashes == null) {
                    modrinthHashes = new AttributeIndex<String>(
                            new TreeMap<String, CandidateBucket>(String.CASE_INSENSITIVE_ORDER));
                }
                modrinthHashes.add(mod.getModrinthHash(), candidate);
            }
            if (mod.getCurseForgeHash() != null) {
                if (curseForgeHashes == null) {
                    curseForgeHashes = new AttributeIndex<Long>(new HashMap<Long, CandidateBucket>());
                }
                curseForgeHashes.add(mod.getCurseForgeHash(), candidate);
            }
        }

        private static IndexedCandidate earlier(IndexedCandidate left, IndexedCandidate right) {
            if (left == null) return right;
            if (right == null) return left;
            return left.order <= right.order ? left : right;
        }
    }

    /** Finds the first remaining candidate whose value is not equal to the excluded value. */
    private static final class AttributeIndex<K> {
        private final Map<K, CandidateBucket> bucketsByValue;
        private final TreeMap<Integer, CandidateBucket> bucketMinima = new TreeMap<Integer, CandidateBucket>();

        private AttributeIndex(Map<K, CandidateBucket> bucketsByValue) {
            this.bucketsByValue = bucketsByValue;
        }

        private void add(K value, IndexedCandidate candidate) {
            CandidateBucket bucket = bucketsByValue.get(value);
            if (bucket == null) {
                bucket = new CandidateBucket();
                bucketsByValue.put(value, bucket);
            } else {
                bucketMinima.remove(bucket.firstOrder());
            }
            bucket.candidates.put(candidate.order, candidate);
            bucketMinima.put(bucket.firstOrder(), bucket);
        }

        private IndexedCandidate firstExcluding(K excludedValue) {
            Map.Entry<Integer, CandidateBucket> first = bucketMinima.firstEntry();
            if (first == null) return null;
            CandidateBucket excludedBucket = bucketsByValue.get(excludedValue);
            if (first.getValue() == excludedBucket) {
                first = bucketMinima.higherEntry(first.getKey());
            }
            return first == null ? null : first.getValue().firstCandidate();
        }

        private void remove(K value, IndexedCandidate candidate) {
            CandidateBucket bucket = bucketsByValue.get(value);
            if (bucket == null) return;
            bucketMinima.remove(bucket.firstOrder());
            bucket.candidates.remove(candidate.order);
            if (bucket.candidates.isEmpty()) {
                bucketsByValue.remove(value);
            } else {
                bucketMinima.put(bucket.firstOrder(), bucket);
            }
        }
    }

    private static final class CandidateBucket {
        private final TreeMap<Integer, IndexedCandidate> candidates =
                new TreeMap<Integer, IndexedCandidate>();

        private int firstOrder() {
            return candidates.firstKey();
        }

        private IndexedCandidate firstCandidate() {
            return candidates.firstEntry().getValue();
        }
    }

    private static final class IndexedCandidate {
        private final Mod mod;
        private final int order;

        private IndexedCandidate(Mod mod, int order) {
            this.mod = mod;
            this.order = order;
        }
    }

    public LinkedHashSet<Mod> getCurrentMods() {
        return new LinkedHashSet<Mod>(currentMods);
    }

    public LinkedHashSet<Mod> getSavedMods() {
        return new LinkedHashSet<Mod>(savedMods);
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

    public int getTotalChanges() {
        return addedMods.size() + removedMods.size() + updatedMods.size();
    }

    public static synchronized ModListDiff getDiff(boolean useCache) {
        return new ModListDiff(ModListUtils.getSavedModList(), ModListUtils.getCurrentModList(useCache));
    }

    public boolean isEmpty() {
        return addedMods.isEmpty() && removedMods.isEmpty() && updatedMods.isEmpty();
    }

    public static boolean isModpackCreator() {
        List<String> modpackCreators = CrashAssistantConfig.getModpackCreators();
        if (modpackCreators.isEmpty() && !CrashAssistantConfig.getBoolean("modpack_modlist.enabled")){
            return false;
        }
        return modpackCreators.contains(ModListUtils.getCurrentUsername()) || modpackCreators.isEmpty();
    }

    public static String getFirstString(boolean forMsg, boolean isMd, String link) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        String part1;
        String part2;
        if (ModListDiff.isModpackCreator()) {
            part1 = langFunc.apply("msg.modlist_changes_latest_launch_1");
            part2 = langFunc.apply("msg.modlist_changes_latest_launch_2");
        } else {
            part1 = langFunc.apply("msg.modlist_changes_modpack_1");
            part2 = langFunc.apply("msg.modlist_changes_modpack_2");
        }
        if (isMd && link != null) {
            return CrashAssistantConfig.get("generated_message.modlist_header_pattern", false)
                    .replace("$PART1$", part1)
                    .replace("$PART2$", part2)
                    .replace("$LINK$", link);
        }
        return part1 + part2;
    }

    public ModListDiffStringBuilder generateDiffMsg(boolean forMsg) {
        return generateDiffMsg(forMsg, getFirstString(forMsg, false, null), true);
    }

    /**
     * Generates the same diff body for an explicitly selected pair of snapshots.
     * The caller supplies the header because historical comparisons are not tied
     * to the global modpack-creator state.
     */
    public ModListDiffStringBuilder generateDiffMsg(boolean forMsg, String header) {
        return generateDiffMsg(forMsg, header, false);
    }

    private ModListDiffStringBuilder generateDiffMsg(boolean forMsg, String header, boolean legacyFirstLaunchHandling) {
        Function<String, String> langFunc = LanguageProvider.getLangFunction(forMsg);
        ModListDiffStringBuilder sb = new ModListDiffStringBuilder();
        if (!CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) return sb;

        sb.append(header == null ? "" : header);
        if (legacyFirstLaunchHandling && isModpackCreator()) {
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
            filePrefix = CrashAssistantConfig.get("generated_message.prefix", false);
        }
        return filePrefix;
    }
}
