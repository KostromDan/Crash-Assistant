package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class ModListHistoryStoreTest {
    private ModListHistoryStoreTest() {
    }

    public static void main(String[] args) throws Exception {
        testFiveStatusesAndTransitions();
        testLaunchTimestampCollisionDoesNotReuseRecord();
        testComparisonReferenceRules();
        testLegacyMigrationAndFallback();
        testCopiedModpackBaselineStaysInPlaceAndIsSeededOnlyOnce();
        testMigrationKeepsSourceWhenVerificationFails();
        System.out.println("mod-list history tests passed.");
    }

    private static void testLaunchTimestampCollisionDoesNotReuseRecord() throws Exception {
        Path root = Files.createTempDirectory("modlist-history-collision");
        try {
            ModListHistoryStore store = new ModListHistoryStore(root.resolve("history"));
            store.beginLaunch(100L, mods("first.jar"));
            ModListHistoryRecord second = store.beginLaunchAtAvailableTimestamp(
                    100L, mods("second.jar"));

            assertEquals(101L, second.getTimestamp(), "occupied start time gets a new numeric id");
            assertEquals("first.jar", required(store.getRecord(100L)).getMods()
                    .iterator().next().getJarName(), "first launch remains intact");
            assertEquals("second.jar", required(store.getRecord(101L)).getMods()
                    .iterator().next().getJarName(), "second launch keeps its own mod list");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testFiveStatusesAndTransitions() throws Exception {
        Path root = Files.createTempDirectory("modlist-history-statuses");
        try {
            ModListHistoryStore store = new ModListHistoryStore(root.resolve("history"));
            LinkedHashSet<Mod> mods = new LinkedHashSet<Mod>();
            Mod nested = new Mod("nested.jar", "nested", "Nested", "2.0", false, true,
                    new HashSet<String>(Arrays.asList("nested.mixins.json")),
                    new ArrayList<Mod>(), "META-INF/jarjar/", 42L, "nested-hash");
            mods.add(new Mod("one.jar", "one", "One", "1.0", true, false,
                    new HashSet<String>(Arrays.asList("one.mixins.json")),
                    new ArrayList<Mod>(Arrays.asList(nested)), null, 24L, "one-hash"));

            store.beginLaunch(100L, mods);
            assertStatus(store, 100L, ModListHistoryStatus.STARTED);
            Mod roundTripped = required(store.getRecord(100L)).getMods().iterator().next();
            assertTrue(roundTripped.getMixinConfigs().contains("one.mixins.json"),
                    "history retains mixin metadata");
            assertEquals(1, roundTripped.getJarJarMods().size(),
                    "history retains Jar-in-Jar metadata");
            assertEquals("nested", roundTripped.getJarJarMods().get(0).getModId(),
                    "history retains nested mod fields");

            store.beginLaunch(200L, mods);
            store.markTitleScreen(200L);
            ModListHistoryRecord title = required(store.getRecord(200L));
            assertEquals(ModListHistoryStatus.TITLE_SCREEN, title.getStatus(), "title status");
            assertTrue(title.hasReachedTitleScreen(), "title milestone retained");
            assertFalse(title.hasReachedJoined(), "title is not joined");

            store.beginLaunch(300L, mods);
            store.markJoined(300L);
            ModListHistoryRecord joined = required(store.getRecord(300L));
            assertEquals(ModListHistoryStatus.JOINED, joined.getStatus(), "joined status");
            assertTrue(joined.hasReachedJoined(), "joined milestone retained");
            assertFalse(joined.hasReachedTitleScreen(), "direct connect does not invent title screen");
            store.markTitleScreen(300L);
            ModListHistoryRecord returnedToTitle = required(store.getRecord(300L));
            assertEquals(ModListHistoryStatus.JOINED, returnedToTitle.getStatus(),
                    "returning to title never downgrades JOINED");
            assertTrue(returnedToTitle.hasReachedTitleScreen(),
                    "later title signal is retained without status downgrade");

            store.beginLaunch(400L, mods);
            store.markJoined(400L);
            store.markCrashAssistantOpened(400L);
            assertStatus(store, 400L, ModListHistoryStatus.CRASHED_DURING_GAMEPLAY);

            store.beginLaunch(500L, mods);
            store.markTitleScreen(500L);
            store.markClosedWithoutCrash(500L);
            ModListHistoryRecord closed = required(store.getRecord(500L));
            assertEquals(ModListHistoryStatus.CLOSED_WITHOUT_CRASH, closed.getStatus(), "closed status");
            assertTrue(closed.hasReachedTitleScreen(), "closed launch retains title milestone");
            assertFalse(closed.hasReachedJoined(), "closed launch retains missing join milestone");

            store.beginLaunch(600L, mods);
            store.markCrashAssistantOpened(600L);
            assertStatus(store, 600L, ModListHistoryStatus.STARTED);

            store.beginLaunch(700L, mods);
            store.markTitleScreen(700L);
            store.markCrashAssistantOpened(700L);
            assertStatus(store, 700L, ModListHistoryStatus.TITLE_SCREEN);
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testComparisonReferenceRules() throws Exception {
        Path root = Files.createTempDirectory("modlist-history-selector");
        try {
            ModListHistoryStore store = new ModListHistoryStore(root.resolve("history"));
            LinkedHashSet<Mod> mods = mods("selector.jar");

            store.beginLaunch(100L, mods);
            store.markJoined(100L);

            store.beginLaunch(200L, mods);
            store.markTitleScreen(200L);

            store.beginLaunch(300L, mods);
            ModListComparisonReference fromStarted = requiredReference(
                    store.findComparisonReference(required(store.getRecord(300L))));
            assertEquals(200L, fromStarted.getRecord().getTimestamp(),
                    "STARTED uses chronologically latest title-or-join");
            assertEquals(ModListComparisonReference.Kind.TITLE_SCREEN, fromStarted.getKind(),
                    "STARTED reference kind");

            store.beginLaunch(400L, mods);
            store.markTitleScreen(400L);
            ModListComparisonReference fromTitle = requiredReference(
                    store.findComparisonReference(required(store.getRecord(400L))));
            assertEquals(100L, fromTitle.getRecord().getTimestamp(),
                    "TITLE_SCREEN never compares to another title-only launch");
            assertEquals(ModListComparisonReference.Kind.JOINED, fromTitle.getKind(),
                    "TITLE_SCREEN uses joined wording");

            store.beginLaunch(500L, mods);
            store.markJoined(500L);
            ModListComparisonReference fromJoined = requiredReference(
                    store.findComparisonReference(required(store.getRecord(500L))));
            assertEquals(100L, fromJoined.getRecord().getTimestamp(),
                    "JOINED excludes the current launch and uses the previous join");

            store.beginLaunch(600L, mods);
            store.markJoined(600L);
            store.markClosedWithoutCrash(600L);
            store.beginLaunch(700L, mods);
            store.markJoined(700L);
            ModListComparisonReference fromAfterClosed = requiredReference(
                    store.findComparisonReference(required(store.getRecord(700L))));
            assertEquals(600L, fromAfterClosed.getRecord().getTimestamp(),
                    "closed launch remains a joined reference through reachedJoined");
            assertEquals(ModListComparisonReference.Kind.JOINED, fromAfterClosed.getKind(),
                    "closed joined launch keeps joined wording");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testLegacyMigrationAndFallback() throws Exception {
        Path root = Files.createTempDirectory("modlist-history-migration");
        try {
            Path history = root.resolve("local/crash_assistant/modlist_history");
            Path legacy = root.resolve("config/crash_assistant/modlist.json");
            Files.createDirectories(legacy.getParent());
            byte[] sourceBytes = Mod.GSON.toJson(mods("старый-мод.jar"), Mod.TYPE)
                    .getBytes(java.nio.charset.Charset.defaultCharset());
            Files.write(legacy, sourceBytes);
            Files.setLastModifiedTime(legacy, FileTime.fromMillis(1234L));

            ModListHistoryStore store = new ModListHistoryStore(history);
            ModListHistoryRecord migrated = required(store.migrateLegacySnapshot(legacy));
            assertTrue(migrated.isLegacySnapshot(), "migration creates an explicit legacy snapshot");
            assertEquals(null, migrated.getStatus(), "legacy snapshot has no launch status");
            assertFalse(migrated.hasReachedTitleScreen(), "legacy does not invent title milestone");
            assertFalse(migrated.hasReachedJoined(), "legacy does not invent join milestone");
            assertEquals("старый-мод.jar", migrated.getMods().iterator().next().getJarName(),
                    "legacy FileWriter charset round-trips non-ASCII names");
            assertFalse(Files.exists(legacy), "source removed only after verified migration");
            assertTrue(Files.isRegularFile(history.resolve(migrated.getTimestamp() + ".json")),
                    "numeric migration filename");

            Files.write(legacy, sourceBytes);
            Files.setLastModifiedTime(legacy, FileTime.fromMillis(1234L));
            ModListHistoryRecord repeated = required(store.migrateLegacySnapshot(legacy));
            assertEquals(migrated.getTimestamp(), repeated.getTimestamp(), "migration is idempotent by fingerprint");
            assertEquals(1, store.listRecordsNewestFirst().size(), "idempotent migration does not duplicate");
            assertFalse(Files.exists(legacy), "idempotent retry completes source removal");

            store.beginLaunch(2000L, mods("current.jar"));
            ModListComparisonReference fallback = requiredReference(
                    store.findComparisonReference(required(store.getRecord(2000L))));
            assertEquals(ModListComparisonReference.Kind.LEGACY_SNAPSHOT, fallback.getKind(),
                    "legacy is only an honest fallback kind");

            store.beginLaunch(3000L, mods("joined.jar"));
            store.markJoined(3000L);
            store.beginLaunch(4000L, mods("new-current.jar"));
            ModListComparisonReference normal = requiredReference(
                    store.findComparisonReference(required(store.getRecord(4000L))));
            assertEquals(ModListComparisonReference.Kind.JOINED, normal.getKind(),
                    "legacy never displaces a trustworthy milestone");
            assertEquals(3000L, normal.getRecord().getTimestamp(), "trustworthy join chosen");

            Path broken = root.resolve("config/crash_assistant/broken.json");
            Files.write(broken, Arrays.asList("not json"), StandardCharsets.UTF_8);
            boolean failed = false;
            try {
                store.migrateLegacySnapshot(broken);
            } catch (IOException expected) {
                failed = true;
            }
            assertTrue(failed, "invalid legacy JSON fails migration");
            assertTrue(Files.isRegularFile(broken), "invalid legacy source is retained");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testCopiedModpackBaselineStaysInPlaceAndIsSeededOnlyOnce() throws Exception {
        Path root = Files.createTempDirectory("modlist-history-modpack-seed");
        try {
            Path history = root.resolve("local/crash_assistant/modlist_history");
            Path baseline = root.resolve("config/crash_assistant/modlist.json");
            Files.createDirectories(baseline.getParent());

            byte[] firstBytes = Mod.GSON.toJson(mods("creator-baseline.jar"), Mod.TYPE)
                    .getBytes(java.nio.charset.Charset.defaultCharset());
            Files.write(baseline, firstBytes);
            Files.setLastModifiedTime(baseline, FileTime.fromMillis(1234L));

            ModListHistoryStore store = new ModListHistoryStore(history);
            ModListHistoryRecord firstCopy = required(store.copyLegacySnapshot(baseline));
            assertTrue(Files.isRegularFile(baseline),
                    "copying a modpack creator baseline leaves modlist.json in place");
            assertEquals("creator-baseline.jar", firstCopy.getMods().iterator().next().getJarName(),
                    "first pre-history modpack baseline seeds history");

            byte[] changedBytes = Mod.GSON.toJson(mods("creator-updated.jar"), Mod.TYPE)
                    .getBytes(java.nio.charset.Charset.defaultCharset());
            Files.write(baseline, changedBytes);
            Files.setLastModifiedTime(baseline, FileTime.fromMillis(5678L));

            ModListHistoryRecord repeatedCopy = required(store.copyLegacySnapshot(baseline));
            assertEquals(firstCopy.getTimestamp(), repeatedCopy.getTimestamp(),
                    "a changed live modpack baseline does not create another migration record");
            assertEquals(1, store.listRecordsNewestFirst().size(),
                    "modpack baseline is seeded into history only once per source path");
            assertTrue(Files.isRegularFile(baseline),
                    "repeated copying still leaves the live baseline in place");
            assertTrue(Arrays.equals(changedBytes, Files.readAllBytes(baseline)),
                    "copying never rewrites the creator's changed baseline");
            assertEquals("creator-baseline.jar",
                    required(store.getRecord(firstCopy.getTimestamp())).getMods()
                            .iterator().next().getJarName(),
                    "the one-time seed remains the pre-history baseline");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testMigrationKeepsSourceWhenVerificationFails() throws Exception {
        Path root = Files.createTempDirectory("modlist-history-verification");
        try {
            Path legacy = root.resolve("modlist.json");
            Files.write(legacy,
                    Mod.GSON.toJson(mods("verification.jar"), Mod.TYPE).getBytes(StandardCharsets.UTF_8));
            ModListHistoryStore store = new ModListHistoryStore(root.resolve("history")) {
                @Override
                public synchronized void save(ModListHistoryRecord record) {
                    // Simulates a destination write that cannot be read back.
                }
            };
            boolean failed = false;
            try {
                store.migrateLegacySnapshot(legacy);
            } catch (IOException expected) {
                failed = true;
            }
            assertTrue(failed, "failed verification aborts migration");
            assertTrue(Files.isRegularFile(legacy), "source survives failed verification");
        } finally {
            deleteRecursively(root);
        }
    }

    private static LinkedHashSet<Mod> mods(String name) {
        LinkedHashSet<Mod> mods = new LinkedHashSet<Mod>();
        mods.add(new Mod(name));
        return mods;
    }

    private static ModListHistoryRecord required(Optional<ModListHistoryRecord> optional) {
        if (!optional.isPresent()) {
            throw new AssertionError("Expected a history record");
        }
        return optional.get();
    }

    private static ModListComparisonReference requiredReference(Optional<ModListComparisonReference> optional) {
        if (!optional.isPresent()) {
            throw new AssertionError("Expected a comparison reference");
        }
        return optional.get();
    }

    private static void assertStatus(ModListHistoryStore store,
                                     long timestamp,
                                     ModListHistoryStatus expected) {
        assertEquals(expected, required(store.getRecord(timestamp)).getStatus(), "status at " + timestamp);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            List<Path> ordered = new java.util.ArrayList<Path>();
            paths.forEach(ordered::add);
            ordered.sort(Comparator.reverseOrder());
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }
}
