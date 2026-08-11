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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Atomic persistence and reference selection for mod-list launch history. */
public class ModListHistoryStore {
    private static final Logger LOGGER = LogManager.getLogger(ModListHistoryStore.class);
    private static final int SCHEMA_VERSION = 1;
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

    public synchronized List<ModListHistoryRecord> listRecordsNewestFirst() {
        if (!Files.isDirectory(historyDirectory)) {
            return Collections.emptyList();
        }
        List<ModListHistoryRecord> records = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDirectory, "*.json")) {
            for (Path path : stream) {
                Optional<ModListHistoryRecord> record = read(path);
                if (record.isPresent()) {
                    records.add(record.get());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list mod-list history in {}", historyDirectory, e);
        }
        records.sort(new Comparator<ModListHistoryRecord>() {
            @Override
            public int compare(ModListHistoryRecord left, ModListHistoryRecord right) {
                return Long.compare(right.getTimestamp(), left.getTimestamp());
            }
        });
        return records;
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
            ModListHistoryRecord current) {
        return findComparisonReference(current, listRecordsNewestFirst());
    }

    /** Uses an already loaded newest-first snapshot list (for example in GUI loaders). */
    public Optional<ModListComparisonReference> findComparisonReference(
            ModListHistoryRecord current,
            List<ModListHistoryRecord> newestFirstRecords) {
        if (current == null) {
            return Optional.empty();
        }

        boolean joinedRequired = current.getStatus().reachedTitleScreen()
                || current.getStatus().reachedJoinedWorld();

        ModListHistoryRecord legacyFallback = null;
        if (newestFirstRecords == null) {
            return Optional.empty();
        }
        for (ModListHistoryRecord candidate : newestFirstRecords) {
            if (candidate.getTimestamp() == current.getTimestamp()) {
                continue;
            }
            if (candidate.isLegacySnapshot()) {
                if (legacyFallback == null) {
                    legacyFallback = candidate;
                }
                continue;
            }
            if (joinedRequired) {
                if (candidate.getStatus().reachedJoinedWorld()) {
                    return Optional.of(new ModListComparisonReference(
                            candidate, ModListComparisonReference.Kind.JOINED));
                }
                continue;
            }

            if (candidate.getStatus().reachedJoinedWorld()) {
                return Optional.of(new ModListComparisonReference(
                        candidate, ModListComparisonReference.Kind.JOINED));
            }
            if (candidate.getStatus().reachedTitleScreen()) {
                return Optional.of(new ModListComparisonReference(
                        candidate, ModListComparisonReference.Kind.TITLE_SCREEN));
            }
        }

        return legacyFallback == null
                ? Optional.<ModListComparisonReference>empty()
                : Optional.of(new ModListComparisonReference(
                        legacyFallback, ModListComparisonReference.Kind.LEGACY_SNAPSHOT));
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

        for (ModListHistoryRecord existing : listRecordsNewestFirst()) {
            if (existing.isLegacySnapshot()
                    && source.equals(existing.getLegacySource())
                    && sourceFingerprint.equals(existing.getLegacyFingerprint())
                    && sameMods(existing.getMods(), legacyMods)) {
                if (deleteSource) {
                    deleteLegacySourceIfUnchanged(legacyJson, sourceFingerprint);
                }
                return Optional.of(existing);
            }
        }

        long timestamp = Math.max(0L, Files.getLastModifiedTime(legacyJson).toMillis());
        while (Files.exists(pathFor(timestamp))) {
            timestamp++;
        }
        ModListHistoryRecord migrated = ModListHistoryRecord.legacy(
                timestamp, legacyMods, source, sourceFingerprint);
        save(migrated);

        Optional<ModListHistoryRecord> verified = getRecord(timestamp);
        if (!verified.isPresent() || !equivalent(migrated, verified.get())) {
            throw new IOException("Failed to verify migrated mod-list snapshot " + pathFor(timestamp));
        }
        if (deleteSource) {
            deleteLegacySourceIfUnchanged(legacyJson, sourceFingerprint);
        }
        return verified;
    }

    /** Finds the one modpack seed without deserializing every history mod list. */
    private Optional<ModListHistoryRecord> findLegacySnapshotBySource(String source) {
        if (!Files.isDirectory(historyDirectory)) {
            return Optional.empty();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDirectory, "*.json")) {
            for (Path path : stream) {
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("\"mods\":")) {
                            break;
                        }
                        if (!trimmed.startsWith("\"legacySource\":")) {
                            continue;
                        }
                        String jsonValue = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                        if (jsonValue.endsWith(",")) {
                            jsonValue = jsonValue.substring(0, jsonValue.length() - 1);
                        }
                        if (source.equals(new JsonParser().parse(jsonValue).getAsString())) {
                            return read(path);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to find a copied legacy mod-list snapshot", e);
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
        long timestamp = root.get("timestamp").getAsLong();
        boolean legacy = getBoolean(root, "legacySnapshot");
        JsonElement modsElement = root.get("mods");
        LinkedHashSet<Mod> mods = modsElement != null && modsElement.isJsonArray()
                ? HISTORY_GSON.<LinkedHashSet<Mod>>fromJson(modsElement, HISTORY_MODS_TYPE)
                : Mod.GSON.<LinkedHashSet<Mod>>fromJson(modsElement, Mod.TYPE);
        if (mods == null) {
            mods = new LinkedHashSet<>();
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

    private static boolean getBoolean(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
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
