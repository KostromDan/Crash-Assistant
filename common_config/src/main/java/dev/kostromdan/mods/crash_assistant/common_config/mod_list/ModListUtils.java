package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryStore;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import org.apache.commons.jexl3.annotations.NoJexl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ModListUtils {
    public static final Logger LOGGER = LogManager.getLogger();
    @NoJexl
    public static Path MODS_FOLDER = Paths.get("mods");
    private static final Path RESOURCEPACKS_FOLDER = Paths.get("resourcepacks");
    private static final Path DATAPACKS_FOLDER = Paths.get("datapacks");
    private static final Path JSON_FILE = Paths.get("config", "crash_assistant", "modlist.json");
    private static final Path MOD_LIST_SCAN_LOCK_PATH = Paths.get("local", "crash_assistant", "mod_list_scan.lock");
    private static final ReentrantLock MOD_LIST_OPERATION_LOCK = new ReentrantLock();
    private static final byte[] UTF_8_BOM = {
            (byte) 0xef, (byte) 0xbb, (byte) 0xbf
    };
    public static String currentUsername = "";
    private static LinkedHashSet<Mod> cachedModList = null;
    private static boolean automaticUpdateScheduled;


    public static LinkedHashSet<Mod> getCurrentModList(boolean useCache) {
        // Preserve the long-standing zero-copy cached-read path. Callers that also
        // need the success bit use ModListScanResult#getMods(), which returns a copy.
        return scanCurrentModListResult(useCache).mods;
    }

    /** Returns the collection and its success state as one immutable, race-free value. */
    public static ModListScanResult scanCurrentModListResult(boolean useCache) {
        MOD_LIST_OPERATION_LOCK.lock();
        try {
            if (cachedModList != null && useCache) {
                return new ModListScanResult(cachedModList, true);
            }
        } finally {
            MOD_LIST_OPERATION_LOCK.unlock();
        }
        try {
            return withModListScanLock(() -> {
                if (cachedModList != null && useCache) {
                    return new ModListScanResult(cachedModList, true);
                }
                LinkedHashSet<Mod> currentMods = scanCurrentModList();
                if (useCache) {
                    cachedModList = currentMods;
                }
                return new ModListScanResult(currentMods, true);
            });
        } catch (Exception e) {
            LOGGER.error("Error while getting current mod list: ", e);
        }
        return new ModListScanResult(new LinkedHashSet<Mod>(), false);
    }

    public static final class ModListScanResult {
        private final LinkedHashSet<Mod> mods;
        private final boolean successful;

        private ModListScanResult(LinkedHashSet<Mod> mods, boolean successful) {
            this.mods = mods == null ? new LinkedHashSet<Mod>() : mods;
            this.successful = successful;
        }

        public LinkedHashSet<Mod> getMods() {
            return new LinkedHashSet<Mod>(mods);
        }

        public boolean isSuccessful() {
            return successful;
        }
    }

    /** Resolves one physical mod file and rejects every path outside the configured mods directory. */
    public static Path resolveDirectModFile(String fileName) {
        if (fileName == null || fileName.isEmpty()
                || ".".equals(fileName) || "..".equals(fileName)
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
                || fileName.indexOf(':') >= 0
                || fileName.endsWith(".") || fileName.endsWith(" ")
                || containsControlCharacter(fileName)) {
            throw new IllegalArgumentException("Mod jar name must be a direct file name: " + fileName);
        }
        Path name;
        try {
            name = Paths.get(fileName);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid mod jar name: " + fileName, e);
        }
        if (name.isAbsolute() || name.getNameCount() != 1 || !fileName.equals(name.getFileName().toString())) {
            throw new IllegalArgumentException("Mod jar name must be a direct file name: " + fileName);
        }
        Path mods = MODS_FOLDER.toAbsolutePath().normalize();
        Path resolved = mods.resolve(name).normalize();
        if (!mods.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Mod file is outside the configured mods directory: " + fileName);
        }
        return resolved;
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Validates an already resolved path before a mod-management file operation. */
    public static Path requireDirectModPath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Mod file path is missing");
        }
        Path normalized = path.toAbsolutePath().normalize();
        Path mods = MODS_FOLDER.toAbsolutePath().normalize();
        if (normalized.getFileName() == null || !mods.equals(normalized.getParent())) {
            throw new IllegalArgumentException("Mod file is outside the configured mods directory: " + path);
        }
        return normalized;
    }

    /** Serializes mod parsing across threads and Crash Assistant JVMs. */
    @NoJexl
    public static <T> T withModListScanLock(Callable<T> operation) throws Exception {
        MOD_LIST_OPERATION_LOCK.lock();
        try {
            if (MOD_LIST_OPERATION_LOCK.getHoldCount() > 1) {
                return operation.call();
            }
            Files.createDirectories(MOD_LIST_SCAN_LOCK_PATH.getParent());
            try (FileChannel lockChannel = FileChannel.open(
                    MOD_LIST_SCAN_LOCK_PATH, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                return operation.call();
            }
        } finally {
            MOD_LIST_OPERATION_LOCK.unlock();
        }
    }

    private static LinkedHashSet<Mod> scanCurrentModList() throws Exception {
        LinkedHashSet<Mod> currentMods = new LinkedHashSet<>();

        if (CrashAssistantConfig.getBoolean("modpack_modlist.add_modloader_jar_name")) {
            currentMods.add(new Mod(
                    PlatformHelp.loaderJarName + " (modloader)",
                    PlatformHelp.platform.name().toLowerCase(),
                    PlatformHelp.platform.name().toLowerCase(),
                    PlatformHelp.loaderJarName,
                    null, null, new HashSet<>(), new ArrayList<>(), null,
                    null, null)
            );
        }

        if (Files.exists(MODS_FOLDER)) {
            long start = System.currentTimeMillis();
            final boolean parserThreadsDaemon = Thread.currentThread().isDaemon();
            final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
            ExecutorService executor = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    runnable -> {
                        Thread thread = defaultThreadFactory.newThread(runnable);
                        thread.setName("CrashAssistant-ModList-Parser-" + thread.getId());
                        thread.setDaemon(parserThreadsDaemon);
                        return thread;
                    });
            List<Future<Mod>> futures = new ArrayList<>();
            try {
                try (Stream<Path> paths = Files.list(MODS_FOLDER)) {
                    paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                            .sorted(new PathComparator())
                            .forEach(path -> futures.add(executor.submit(() -> ModDataParser.parseModData(path))));
                }
                for (Future<Mod> future : futures) {
                    currentMods.add(future.get());
                }
            } finally {
                for (Future<Mod> future : futures) {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                }
                executor.shutdownNow();
            }
            LOGGER.info("Parsed " + currentMods.size() + " mod(s) metadata in " + (System.currentTimeMillis() - start) + " ms");
        }
        if (Files.exists(RESOURCEPACKS_FOLDER) && CrashAssistantConfig.getBoolean("modpack_modlist.add_resourcepacks")) {
            try (Stream<Path> paths = Files.list(RESOURCEPACKS_FOLDER)) {
                paths.sorted(new PathComparator()).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (Files.isDirectory(path) || filename.endsWith(".zip")) {
                        currentMods.add(new Mod(filename + " (resourcepack)"));
                    }
                });
            }
        }
        if (Files.exists(DATAPACKS_FOLDER) && CrashAssistantConfig.getBoolean("modpack_modlist.add_datapacks")) {
            try (Stream<Path> paths = Files.list(DATAPACKS_FOLDER)) {
                paths.sorted(new PathComparator()).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (Files.isDirectory(path) || filename.endsWith(".zip")) {
                        currentMods.add(new Mod(filename + " (datapack)"));
                    }
                });
            }
        }
        return currentMods;
    }

    public static Map<String, Mod> getCurrentModListMappedToModId() {
        return getCurrentModListMappedToModId(false);
    }

    public static Map<String, Mod> getCurrentModListMappedToModId(boolean includeJarInJarEntries) {
        LinkedHashMap<String, Mod> modsById = new LinkedHashMap<>();
        LinkedHashSet<Mod> currentMods = getCurrentModList(true);

        if (includeJarInJarEntries) {
            forEachModRecursive(currentMods, mod -> putIfAbsentByModId(modsById, mod));
        } else {
            for (Mod mod : currentMods) {
                putIfAbsentByModId(modsById, mod);
            }
        }

        return modsById;
    }

    public static LinkedHashSet<Mod> getSavedModList() {
        try {
            return readModList(JSON_FILE);
        } catch (Exception e) {
            LOGGER.error("Error while getting Modlist", e);
        }
        return new LinkedHashSet<>();
    }

    static LinkedHashSet<Mod> readModList(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new LinkedHashSet<>();
        }
        return parseModListJson(Files.readAllBytes(path));
    }

    static void writeModList(Path path, Collection<Mod> mods) throws IOException {
        Path parent = path.getParent();
        Path directory = parent == null ? Paths.get(".") : parent;
        Files.createDirectories(directory);
        byte[] json = Mod.GSON.toJson(mods, Mod.TYPE).getBytes(StandardCharsets.UTF_8);
        byte[] encoded = new byte[UTF_8_BOM.length + json.length];
        System.arraycopy(UTF_8_BOM, 0, encoded, 0, UTF_8_BOM.length);
        System.arraycopy(json, 0, encoded, UTF_8_BOM.length, json.length);
        Path temporary = Files.createTempFile(directory, ".modlist-", ".tmp");
        try {
            Files.write(temporary, encoded,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(temporary, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Parses the new UTF-8+BOM format and both legacy no-BOM formats. */
    @NoJexl
    public static LinkedHashSet<Mod> parseModListJson(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new LinkedHashSet<>();
        }
        boolean hasBom = hasUtf8Bom(bytes);
        int offset = hasBom ? UTF_8_BOM.length : 0;
        RuntimeException firstFailure = null;
        LinkedHashSet<Charset> charsets = new LinkedHashSet<>();
        charsets.add(StandardCharsets.UTF_8);
        if (!hasBom) {
            addCharset(charsets, System.getProperty("native.encoding"));
            charsets.add(Charset.defaultCharset());
        }
        for (Charset charset : charsets) {
            try {
                return parseDecodedModList(decodeStrict(bytes, offset, charset));
            } catch (CharacterCodingException | RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = new IllegalArgumentException(
                            "Invalid modlist.json for charset " + charset.name(), failure);
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        throw firstFailure == null
                ? new IllegalArgumentException("Invalid modlist.json")
                : firstFailure;
    }

    private static String decodeStrict(byte[] bytes, int offset, Charset charset)
            throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                .toString();
    }

    private static void addCharset(Set<Charset> charsets, String charsetName) {
        if (charsetName == null || charsetName.isEmpty()) {
            return;
        }
        try {
            charsets.add(Charset.forName(charsetName));
        } catch (RuntimeException ignored) {
            // Ignore unavailable or malformed values supplied by the runtime.
        }
    }

    private static LinkedHashSet<Mod> parseDecodedModList(String json) {
        LinkedHashSet<Mod> mods = Mod.GSON.fromJson(json, Mod.TYPE);
        return mods == null ? new LinkedHashSet<Mod>() : mods;
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= UTF_8_BOM.length
                && bytes[0] == UTF_8_BOM[0]
                && bytes[1] == UTF_8_BOM[1]
                && bytes[2] == UTF_8_BOM[2];
    }

    @NoJexl
    public static void forEachModRecursive(Collection<Mod> mods, Consumer<Mod> consumer) {
        if (mods == null || consumer == null) {
            return;
        }
        for (Mod mod : mods) {
            forEachModRecursive(mod, consumer);
        }
    }

    @NoJexl
    public static void saveCurrentModList() {
        if (PlatformHelp.isLinkDefault()) {
            LOGGER.info("Skipping modlist.json update in an ordinary installation; launch snapshots are stored in modlist_history.");
            return;
        }
        try {
            withModListScanLock(() -> {
                ModListScanResult scan = scanCurrentModListResult(false);
                if (scan.isSuccessful()) {
                    LinkedHashSet<Mod> mods = scan.getMods();
                    // Preserve the pre-history modpack baseline even when the
                    // title-screen update wins the race with the snapshot worker.
                    try {
                        ModListHistoryStore.getDefault().copyLegacySnapshot(JSON_FILE);
                    } catch (Exception e) {
                        LOGGER.error("Failed to preserve the pre-history modlist.json", e);
                    }
                    writeModList(JSON_FILE, mods);
                    LOGGER.info("Modlist saved to " + JSON_FILE);
                }
                return null;
            });
        } catch (Exception e) {
            LOGGER.error("Error while saving Modlist", e);
        }
    }

    @NoJexl
    public static synchronized void scheduleAutomaticUpdate(String username) {
        if (automaticUpdateScheduled) {
            return;
        }
        automaticUpdateScheduled = true;
        if (username != null && !username.isEmpty()) {
            currentUsername = username;
        }
        Thread updateThread = new Thread(ModListUtils::runAutomaticUpdate, "CrashAssistant-ModListUpdate");
        updateThread.start();
    }

    private static void runAutomaticUpdate() {
        try {
            if (!CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
                return;
            }
            String username = getCurrentUsername();
            if (username == null || username.isEmpty()) {
                LOGGER.warn("Cannot auto-update modlist.json: current username is unavailable.");
                return;
            }
            if (CrashAssistantConfig.getModpackCreators().isEmpty()) {
                CrashAssistantConfig.addModpackCreator(username);
            }
            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update")
                    && CrashAssistantConfig.getModpackCreators().contains(username)) {
                saveCurrentModList();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to auto-update modlist.json", e);
        }
    }

    public static String getCurrentUsername() {
        if (currentUsername.isEmpty()) {
            Optional<String> x = ProcessSignalIO.getInfo("username");
            x.ifPresent(s -> currentUsername = s);
        }
        return currentUsername;
    }

    @NoJexl
    public static Path getSavedModListPath() {
        return JSON_FILE;
    }

    @NoJexl
    private static void forEachModRecursive(Mod mod, Consumer<Mod> consumer) {
        if (mod == null) {
            return;
        }

        consumer.accept(mod);
        List<Mod> children = mod.getJarJarMods();
        if (children == null || children.isEmpty()) {
            return;
        }

        for (Mod child : children) {
            forEachModRecursive(child, consumer);
        }
    }

    @NoJexl
    private static void putIfAbsentByModId(Map<String, Mod> modsById, Mod mod) {
        if (mod == null || mod.getModId() == null) {
            return;
        }
        modsById.putIfAbsent(mod.getModId(), mod);
    }
}
