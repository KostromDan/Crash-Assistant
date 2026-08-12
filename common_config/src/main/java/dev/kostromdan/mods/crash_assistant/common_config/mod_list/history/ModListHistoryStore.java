package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Atomic persistence and reference selection for mod-list launch history. */
public class ModListHistoryStore {
    private static final Logger LOGGER = LogManager.getLogger(ModListHistoryStore.class);
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_SUMMARY_HEADER_CHARS = 64 * 1024;
    private static final Type HISTORY_MODS_TYPE = new TypeToken<LinkedHashSet<Mod>>() {
    }.getType();
    private static final Gson HISTORY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DEFAULT_HISTORY_DIRECTORY =
            Paths.get("local", "crash_assistant", "modlist_history");
    private static final ModListHistoryStore DEFAULT =
            new ModListHistoryStore(DEFAULT_HISTORY_DIRECTORY);

    private final Path historyDirectory;

    public ModListHistoryStore(Path historyDirectory) {
        if (historyDirectory == null) {
            throw new IllegalArgumentException("historyDirectory is required");
        }
        this.historyDirectory = historyDirectory;
    }

    public static ModListHistoryStore getDefault() {
        return DEFAULT;
    }

    public static Path getHistoryDirectory() {
        return DEFAULT_HISTORY_DIRECTORY;
    }

    public Path getDirectory() {
        return historyDirectory;
    }

    public synchronized ModListHistoryRecord beginLaunch(long launchStartedAt, LinkedHashSet<Mod> mods)
            throws IOException {
        ModListHistoryRecord record = ModListHistoryRecord.started(launchStartedAt, mods);
        save(record);
        return record;
    }

    /**
     * Creates a new launch without ever reusing an occupied numeric filename.
     * Some process-start providers have only one-second precision, so two real
     * launches can legitimately arrive with the same preferred timestamp.
     */
    public synchronized ModListHistoryRecord beginLaunchAtAvailableTimestamp(
            long preferredTimestamp,
            LinkedHashSet<Mod> mods) throws IOException {
        Files.createDirectories(historyDirectory);
        long timestamp = Math.max(0L, preferredTimestamp);
        Path reservation;
        while (true) {
            reservation = pathFor(timestamp);
            try {
                Files.createFile(reservation);
                break;
            } catch (FileAlreadyExistsException occupied) {
                if (timestamp == Long.MAX_VALUE) {
                    throw new IOException("No numeric timestamp is available for mod-list history", occupied);
                }
                timestamp++;
            }
        }

        ModListHistoryRecord record = ModListHistoryRecord.started(timestamp, mods);
        try {
            save(record);
            return record;
        } catch (IOException e) {
            Files.deleteIfExists(reservation);
            throw e;
        } catch (RuntimeException e) {
            Files.deleteIfExists(reservation);
            throw e;
        }
    }

    public synchronized void save(ModListHistoryRecord record) throws IOException {
        Files.createDirectories(historyDirectory);
        Path target = pathFor(record.getTimestamp());
        Path temporary = Files.createTempFile(historyDirectory, "." + record.getTimestamp() + "-", ".tmp");
        try {
            Files.write(
                    temporary,
                    serialize(record).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Atomically changes only the root status without materializing the mod list.
     * Returns {@code false} when the requested launch record does not exist.
     */
    public synchronized boolean updateStatus(long timestamp, ModListHistoryStatus status)
            throws IOException {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        Path target = pathFor(timestamp);
        if (!Files.isRegularFile(target)) {
            return false;
        }

        Files.createDirectories(historyDirectory);
        Path temporary = Files.createTempFile(historyDirectory, "." + timestamp + "-", ".tmp");
        try {
            rewriteStatus(target, temporary, timestamp, status);
            moveAtomically(temporary, target);
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized Optional<ModListHistoryRecord> getRecord(long timestamp) {
        return read(pathFor(timestamp));
    }

    /** Lists only record metadata; mod graphs stay on disk until a record is explicitly opened. */
    public synchronized List<ModListHistorySummary> listSummariesNewestFirst() {
        if (!Files.isDirectory(historyDirectory)) {
            return Collections.emptyList();
        }
        List<ModListHistorySummary> summaries = new ArrayList<ModListHistorySummary>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDirectory, "*.json")) {
            for (Path path : stream) {
                Optional<ModListHistorySummary> summary = readSummary(path);
                if (summary.isPresent()) summaries.add(summary.get());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list mod-list history summaries in {}", historyDirectory, e);
        }
        summaries.sort((left, right) -> Long.compare(right.getTimestamp(), left.getTimestamp()));
        return summaries;
    }

    public synchronized Optional<ModListHistorySummary> getSummary(long timestamp) {
        return readSummary(pathFor(timestamp));
    }

    /** Deletes only an unreconciled launch record, never a migrated or finalized record. */
    public synchronized boolean deleteIfStillStarted(long timestamp) throws IOException {
        Optional<ModListHistorySummary> summary = getSummary(timestamp);
        if (!summary.isPresent()
                || summary.get().isLegacySnapshot()
                || summary.get().getStatus() != ModListHistoryStatus.STARTED) {
            return false;
        }
        return Files.deleteIfExists(pathFor(timestamp));
    }

    /**
     * Selects the dynamic baseline requested by the mod-list widget.
     *
     * <ul>
     *     <li>STARTED: newest previous launch that reached title or joined;</li>
     *     <li>title reached but not joined: newest previous joined launch;</li>
     *     <li>joined/gameplay crash: newest previous joined launch.</li>
     * </ul>
     * A migrated pre-history snapshot is returned only when no trustworthy
     * milestone candidate exists.
     */
    public synchronized Optional<ModListComparisonReference> findComparisonReference(
            ModListHistorySummary current) {
        if (current == null || current.isLegacySnapshot()) return Optional.empty();
        Optional<ModListHistorySummary> selected = findComparisonReferenceSummaryOnDisk(
                current.getTimestamp(), current.getStatus());
        if (!selected.isPresent()) return Optional.empty();
        Optional<ModListHistoryRecord> record = getRecord(selected.get().getTimestamp());
        if (!record.isPresent()) return Optional.empty();
        return Optional.of(new ModListComparisonReference(record.get(), referenceKind(selected.get())));
    }

    /**
     * Finds the automatic comparison baseline without retaining metadata objects
     * for every launch. Numeric filenames are ordered newest-first, then bounded
     * headers are inspected one at a time until the first suitable launch is found.
     */
    private Optional<ModListHistorySummary> findComparisonReferenceSummaryOnDisk(
            long currentTimestamp,
            ModListHistoryStatus currentStatus) {
        long[] timestamps = listHistoryTimestampsAscending();
        boolean joinedRequired = currentStatus.reachedTitleScreen()
                || currentStatus.reachedJoinedWorld();
        ModListHistorySummary olderLegacyFallback = null;

        // Normal candidates must be predecessors of the current launch. In the
        // usual case this loop reads only a handful of small JSON headers.
        for (int i = timestamps.length - 1; i >= 0; i--) {
            long timestamp = timestamps[i];
            if (timestamp >= currentTimestamp) continue;
            Optional<ModListHistorySummary> candidateOptional = readSummary(pathFor(timestamp));
            if (!candidateOptional.isPresent()) continue;
            ModListHistorySummary candidate = candidateOptional.get();
            if (candidate.isLegacySnapshot()) {
                if (olderLegacyFallback == null) olderLegacyFallback = candidate;
                continue;
            }
            if (isEligibleComparisonReference(candidate, joinedRequired)) {
                return Optional.of(candidate);
            }
        }

        // Legacy seeds are deliberately allowed to have a timestamp newer than
        // the current launch (their filename originates from source mtime). They
        // remain only a fallback when no real milestone candidate exists.
        for (int i = timestamps.length - 1; i >= 0; i--) {
            long timestamp = timestamps[i];
            if (timestamp <= currentTimestamp) break;
            Optional<ModListHistorySummary> candidateOptional = readSummary(pathFor(timestamp));
            if (candidateOptional.isPresent() && candidateOptional.get().isLegacySnapshot()) {
                return candidateOptional;
            }
        }
        return Optional.ofNullable(olderLegacyFallback);
    }

    private static boolean isEligibleComparisonReference(
            ModListHistorySummary candidate,
            boolean joinedRequired) {
        return joinedRequired
                ? candidate.getStatus().reachedJoinedWorld()
                : candidate.getStatus().reachedJoinedWorld()
                || candidate.getStatus().reachedTitleScreen();
    }

    /**
     * Materializes only primitive timestamps (not summaries or mod graphs).
     * Directory enumeration and the fixed eight radix passes are both O(N).
     */
    private long[] listHistoryTimestampsAscending() {
        if (!Files.isDirectory(historyDirectory)) return new long[0];
        long[] timestamps = new long[16];
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDirectory, "*.json")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String numericPart = fileName.substring(0, fileName.length() - ".json".length());
                long timestamp;
                try {
                    timestamp = Long.parseLong(numericPart);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (timestamp < 0L || !fileName.equals(Long.toString(timestamp) + ".json")) continue;
                if (count == timestamps.length) {
                    timestamps = Arrays.copyOf(timestamps, timestamps.length * 2);
                }
                timestamps[count++] = timestamp;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list mod-list history filenames in {}", historyDirectory, e);
            return new long[0];
        }
        timestamps = Arrays.copyOf(timestamps, count);
        radixSortNonNegativeLongs(timestamps);
        return timestamps;
    }

    /** Stable LSD radix sort: eight linear passes for non-negative timestamps. */
    private static void radixSortNonNegativeLongs(long[] values) {
        if (values.length < 2) return;
        long[] scratch = new long[values.length];
        long[] source = values;
        long[] destination = scratch;
        int[] counts = new int[256];
        int[] positions = new int[256];
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            Arrays.fill(counts, 0);
            for (long value : source) {
                counts[(int) ((value >>> shift) & 0xffL)]++;
            }
            int position = 0;
            for (int bucket = 0; bucket < counts.length; bucket++) {
                positions[bucket] = position;
                position += counts[bucket];
            }
            for (long value : source) {
                int bucket = (int) ((value >>> shift) & 0xffL);
                destination[positions[bucket]++] = value;
            }
            long[] swap = source;
            source = destination;
            destination = swap;
        }
        if (source != values) {
            System.arraycopy(source, 0, values, 0, values.length);
        }
    }

    public Optional<ModListHistorySummary> findComparisonReferenceSummary(
            long currentTimestamp, ModListHistoryStatus currentStatus,
            List<ModListHistorySummary> newestFirstSummaries) {
        boolean joinedRequired = currentStatus.reachedTitleScreen() || currentStatus.reachedJoinedWorld();
        ModListHistorySummary legacyFallback = null;
        if (newestFirstSummaries == null) return Optional.empty();
        for (ModListHistorySummary candidate : newestFirstSummaries) {
            if (candidate.getTimestamp() == currentTimestamp) continue;
            if (candidate.isLegacySnapshot()) {
                if (legacyFallback == null) legacyFallback = candidate;
                continue;
            }
            // Concurrent/newer launches are not predecessors of this launch and
            // must never drive actions against its installed mod set.
            if (candidate.getTimestamp() > currentTimestamp) continue;
            if (isEligibleComparisonReference(candidate, joinedRequired)) {
                return Optional.of(candidate);
            }
        }
        return Optional.ofNullable(legacyFallback);
    }

    private static ModListComparisonReference.Kind referenceKind(ModListHistorySummary summary) {
        if (summary.isLegacySnapshot()) return ModListComparisonReference.Kind.LEGACY_SNAPSHOT;
        return summary.getStatus().reachedJoinedWorld()
                ? ModListComparisonReference.Kind.JOINED
                : ModListComparisonReference.Kind.TITLE_SCREEN;
    }

    public synchronized Optional<ModListHistoryRecord> migrateLegacySnapshot(Path legacyJson)
            throws IOException {
        return importLegacySnapshot(legacyJson, true);
    }

    /** Seeds history from a modpack baseline while deliberately leaving it in place. */
    public synchronized Optional<ModListHistoryRecord> copyLegacySnapshot(Path legacyJson)
            throws IOException {
        return importLegacySnapshot(legacyJson, false);
    }

    private Optional<ModListHistoryRecord> importLegacySnapshot(
            Path legacyJson,
            boolean deleteSource) throws IOException {
        if (legacyJson == null || !Files.isRegularFile(legacyJson)) {
            return Optional.empty();
        }

        String source = legacyJson.toString();
        if (!deleteSource) {
            Optional<ModListHistoryRecord> existing = findLegacySnapshotBySource(source);
            if (existing.isPresent()) {
                return existing;
            }
        }

        byte[] sourceBytes = Files.readAllBytes(legacyJson);
        String sourceFingerprint = sha256(sourceBytes);
        LinkedHashSet<Mod> legacyMods;
        try {
            legacyMods = ModListUtils.parseModListJson(sourceBytes);
        } catch (RuntimeException e) {
            throw new IOException("Failed to parse legacy mod list " + legacyJson, e);
        }

        for (ModListHistorySummary summary : listSummariesNewestFirst()) {
            if (!summary.isLegacySnapshot() || !source.equals(summary.getLegacySource())) continue;
            Optional<ModListHistoryRecord> existingRecord = getRecord(summary.getTimestamp());
            if (!existingRecord.isPresent()) continue;
            ModListHistoryRecord existing = existingRecord.get();
            if (sourceFingerprint.equals(existing.getLegacyFingerprint())
                    && sameMods(existing.getMods(), legacyMods)) {
                if (deleteSource) {
                    deleteLegacySourceIfUnchanged(legacyJson, sourceFingerprint);
                }
                return Optional.of(existing);
            }
        }

        ModListHistoryRecord migrated = saveLegacyAtAvailableTimestamp(
                Files.getLastModifiedTime(legacyJson).toMillis(),
                legacyMods,
                source,
                sourceFingerprint);
        long timestamp = migrated.getTimestamp();

        Optional<ModListHistoryRecord> verified = getRecord(timestamp);
        if (!verified.isPresent() || !equivalent(migrated, verified.get())) {
            throw new IOException("Failed to verify migrated mod-list snapshot " + pathFor(timestamp));
        }
        if (deleteSource) {
            deleteLegacySourceIfUnchanged(legacyJson, sourceFingerprint);
        }
        return verified;
    }

    /**
     * Reserves the numeric filename before replacing it with the migrated record.
     * The filesystem reservation prevents a concurrent launch JVM from selecting
     * and then being overwritten at the same timestamp.
     */
    private ModListHistoryRecord saveLegacyAtAvailableTimestamp(
            long preferredTimestamp,
            LinkedHashSet<Mod> mods,
            String source,
            String fingerprint) throws IOException {
        Files.createDirectories(historyDirectory);
        long timestamp = Math.max(0L, preferredTimestamp);
        Path reservation;
        while (true) {
            reservation = pathFor(timestamp);
            try {
                Files.createFile(reservation);
                break;
            } catch (FileAlreadyExistsException occupied) {
                if (timestamp == Long.MAX_VALUE) {
                    throw new IOException("No numeric timestamp is available for mod-list history", occupied);
                }
                timestamp++;
            }
        }

        ModListHistoryRecord record = ModListHistoryRecord.legacy(
                timestamp, mods, source, fingerprint);
        try {
            save(record);
            return record;
        } catch (IOException e) {
            Files.deleteIfExists(reservation);
            throw e;
        } catch (RuntimeException e) {
            Files.deleteIfExists(reservation);
            throw e;
        }
    }

    /** Finds the one modpack seed without deserializing every history mod list. */
    private Optional<ModListHistoryRecord> findLegacySnapshotBySource(String source) {
        for (ModListHistorySummary summary : listSummariesNewestFirst()) {
            if (summary.isLegacySnapshot() && source.equals(summary.getLegacySource())) {
                return getRecord(summary.getTimestamp());
            }
        }
        return Optional.empty();
    }

    private Path pathFor(long timestamp) {
        return historyDirectory.resolve(Long.toString(timestamp) + ".json");
    }

    private Optional<ModListHistoryRecord> read(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return Optional.of(deserialize(json));
        } catch (Exception e) {
            LOGGER.error("Failed to read mod-list history record {}", path, e);
            return Optional.empty();
        }
    }

    private static String serialize(ModListHistoryRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("timestamp", record.getTimestamp());
        root.addProperty("modsCount", record.getModsCount());
        if (record.getStatus() != null) {
            root.addProperty("status", record.getStatus().name());
        }
        if (record.isLegacySnapshot()) {
            root.addProperty("legacySnapshot", true);
            if (record.getLegacySource() != null) {
                root.addProperty("legacySource", record.getLegacySource());
            }
            if (record.getLegacyFingerprint() != null) {
                root.addProperty("legacyFingerprint", record.getLegacyFingerprint());
            }
        }
        // History keeps the complete scan (including mixin/Jar-in-Jar metadata),
        // while the long-standing modpack baseline adapter intentionally stores
        // only its smaller compatibility subset.
        root.add("mods", HISTORY_GSON.toJsonTree(record.getMods(), HISTORY_MODS_TYPE));
        return Mod.GSON.toJson(root);
    }

    private static ModListHistoryRecord deserialize(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        if (!root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != SCHEMA_VERSION
                || !root.has("modsCount")) {
            throw new IllegalArgumentException("Unsupported or incomplete mod-list history schema");
        }
        long timestamp = root.get("timestamp").getAsLong();
        int modsCount = root.get("modsCount").getAsInt();
        boolean legacy = getBoolean(root, "legacySnapshot");
        JsonElement modsElement = root.get("mods");
        if (modsElement == null || !modsElement.isJsonArray()) {
            throw new IllegalArgumentException("History record has no mod graph");
        }
        LinkedHashSet<Mod> mods = HISTORY_GSON.fromJson(modsElement, HISTORY_MODS_TYPE);
        if (mods == null) {
            mods = new LinkedHashSet<>();
        }
        if (modsCount < 0 || modsCount != mods.size()) {
            throw new IllegalArgumentException("History record modsCount does not match its mod graph");
        }
        if (legacy) {
            String source = root.has("legacySource") && !root.get("legacySource").isJsonNull()
                    ? root.get("legacySource").getAsString()
                    : null;
            String fingerprint = root.has("legacyFingerprint") && !root.get("legacyFingerprint").isJsonNull()
                    ? root.get("legacyFingerprint").getAsString()
                    : null;
            return ModListHistoryRecord.legacy(timestamp, mods, source, fingerprint);
        }
        ModListHistoryStatus status = ModListHistoryStatus.valueOf(root.get("status").getAsString());
        return new ModListHistoryRecord(
                timestamp,
                status,
                mods);
    }

    private Optional<ModListHistorySummary> readSummary(Path path) {
        if (!Files.isRegularFile(path)) return Optional.empty();
        try (Reader fileReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(
                     new LimitedReader(fileReader, MAX_SUMMARY_HEADER_CHARS))) {
            Integer schemaVersion = null;
            Long timestamp = null;
            Integer modsCount = null;
            ModListHistoryStatus status = null;
            boolean legacy = false;
            String legacySource = null;
            boolean modsFieldFound = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("\"mods\":")) {
                    modsFieldFound = true;
                    break;
                }
                int colon = trimmed.indexOf(':');
                if (colon < 0) continue;
                String key = trimmed.substring(0, colon).replace("\"", "").trim();
                String value = trimmed.substring(colon + 1).trim();
                if (value.endsWith(",")) value = value.substring(0, value.length() - 1);
                if ("schemaVersion".equals(key)) schemaVersion = Integer.valueOf(value);
                else if ("timestamp".equals(key)) timestamp = Long.valueOf(value);
                else if ("modsCount".equals(key)) modsCount = Integer.valueOf(value);
                else if ("status".equals(key)) status = ModListHistoryStatus.valueOf(
                        new JsonParser().parse(value).getAsString());
                else if ("legacySnapshot".equals(key)) legacy = Boolean.parseBoolean(value);
                else if ("legacySource".equals(key)) legacySource = new JsonParser().parse(value).getAsString();
            }
            if (schemaVersion == null || schemaVersion.intValue() != SCHEMA_VERSION
                    || timestamp == null || timestamp.longValue() < 0L
                    || modsCount == null || modsCount.intValue() < 0
                    || !modsFieldFound || legacy == (status != null)
                    || !path.getFileName().toString().equals(timestamp + ".json")) {
                throw new IOException("Incomplete mod-list history summary " + path);
            }
            return Optional.of(new ModListHistorySummary(
                    timestamp.longValue(), status, modsCount.intValue(), legacy, legacySource));
        } catch (Exception e) {
            LOGGER.error("Failed to read mod-list history summary {}", path, e);
            return Optional.empty();
        }
    }

    private static boolean getBoolean(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    /** Prevents a corrupt/minified history file from allocating an unbounded header line. */
    private static final class LimitedReader extends Reader {
        private final Reader delegate;
        private int remaining;

        private LimitedReader(Reader delegate, int limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = delegate.read(buffer, offset, Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static boolean equivalent(ModListHistoryRecord expected, ModListHistoryRecord actual) {
        return expected.getTimestamp() == actual.getTimestamp()
                && expected.getStatus() == actual.getStatus()
                && expected.isLegacySnapshot() == actual.isLegacySnapshot()
                && java.util.Objects.equals(expected.getLegacySource(), actual.getLegacySource())
                && java.util.Objects.equals(expected.getLegacyFingerprint(), actual.getLegacyFingerprint())
                && sameMods(expected.getMods(), actual.getMods());
    }

    private static boolean sameMods(LinkedHashSet<Mod> left, LinkedHashSet<Mod> right) {
        return HISTORY_GSON.toJson(left, HISTORY_MODS_TYPE)
                .equals(HISTORY_GSON.toJson(right, HISTORY_MODS_TYPE));
    }

    private static void rewriteStatus(Path source,
                                      Path destination,
                                      long expectedTimestamp,
                                      ModListHistoryStatus status) throws IOException {
        boolean timestampFound = false;
        boolean statusFound = false;
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(
                     destination,
                     StandardCharsets.UTF_8,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("  \"timestamp\": ")) {
                    String value = line.substring(15).replace(",", "").trim();
                    if (timestampFound || Long.parseLong(value) != expectedTimestamp) {
                        throw new IOException("Unexpected timestamp in mod-list history record " + source);
                    }
                    timestampFound = true;
                } else if (line.startsWith("  \"status\": ")) {
                    if (statusFound) {
                        throw new IOException("Duplicate status in mod-list history record " + source);
                    }
                    line = "  \"status\": \"" + status.name() + "\",";
                    statusFound = true;
                }
                writer.write(line);
                writer.newLine();
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid timestamp in mod-list history record " + source, e);
        }
        if (!timestampFound || !statusFound) {
            throw new IOException("Incomplete mod-list history record " + source);
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static void deleteLegacySourceIfUnchanged(Path source, String expectedFingerprint)
            throws IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        String currentFingerprint = sha256(Files.readAllBytes(source));
        if (!expectedFingerprint.equals(currentFingerprint)) {
            throw new IOException("Legacy mod-list source changed during migration; keeping " + source);
        }
        Files.delete(source);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
