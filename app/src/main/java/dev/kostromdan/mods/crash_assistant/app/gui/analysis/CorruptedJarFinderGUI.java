package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

public class CorruptedJarFinderGUI extends AnalysisGUIBase {

    private static final String ERROR_PLACEHOLDER = "$ERROR$";

    private enum CorruptionReason {
        MISSING("gui.analysis.corrupted_jar_finder.reason.missing", false),
        NOT_A_FILE("gui.analysis.corrupted_jar_finder.reason.not_a_file", false),
        EMPTY("gui.analysis.corrupted_jar_finder.reason.empty", false),
        ZIP_ERROR("gui.analysis.corrupted_jar_finder.reason.zip_error", true),
        IO_ERROR("gui.analysis.corrupted_jar_finder.reason.io_error", true);

        private final String langKey;
        private final boolean usesError;

        CorruptionReason(String langKey, boolean usesError) {
            this.langKey = langKey;
            this.usesError = usesError;
        }

        public String format(String detail) {
            String template = LanguageProvider.get(langKey);
            if (usesError) {
                String replacement = detail == null ? "" : detail;
                return template.replace(ERROR_PLACEHOLDER, replacement);
            }
            return template;
        }
    }

    private static final class CorruptionRecord {
        final String containerId;   // full nested path id (top.jar!/path/inner.jar!/...)
        final CorruptionReason reason;
        final String detail;

        CorruptionRecord(String containerId, CorruptionReason reason, String detail) {
            this.containerId = containerId;
            this.reason = reason;
            this.detail = detail;
        }

        String formattedReason() {
            return reason.format(detail);
        }
    }

    public CorruptedJarFinderGUI(JFrame parent) {
        super(
                parent,
                LanguageProvider.get("gui.menu.analysis.corrupted_jar_finder"),
                LanguageProvider.get("gui.analysis.corrupted_jar_finder.header")
        );
    }

    public static void showDialog(JFrame parent) {
        new CorruptedJarFinderGUI(parent).start();
    }

    @Override
    protected void performAnalysis() {
        List<Path> archives = discoverArchives();
        int totalArchives = archives.size();
        AtomicInteger completedTasks = new AtomicInteger(0);
        AtomicBoolean headerShown = new AtomicBoolean(false);
        AtomicBoolean anyCorruptionFound = new AtomicBoolean(false);

        SwingUtilities.invokeLater(() -> progressBar.setMaximum(Math.max(1, totalArchives)));

        for (Path jarPath : archives) {
            executor.submit(() -> {
                if (isCancelled) return;

                String topId = toModsRelative(jarPath);

                SwingUtilities.invokeLater(() ->
                        currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + topId)
                );

                List<CorruptionRecord> records = inspectJarAndNested(jarPath, topId);

                if (!records.isEmpty()) {
                    if (Files.exists(jarPath)) {
                        registerDetectedModJar(topId);
                    }
                    anyCorruptionFound.set(true);
                    logCorruptions(records);

                    List<CorruptionRecord> modRecords = Collections.unmodifiableList(new ArrayList<>(records));
                    SwingUtilities.invokeLater(() -> {
                        boolean first = headerShown.compareAndSet(false, true);
                        appendResultsForMod(topId, modRecords, first);
                    });
                }

                int completed = completedTasks.incrementAndGet();
                SwingUtilities.invokeLater(() -> {
                    if (!isCancelled) progressBar.setValue(completed);
                });
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!isCancelled && !anyCorruptionFound.get()) {
            SwingUtilities.invokeLater(() ->
                    appendStyledText(LanguageProvider.get("gui.analysis.corrupted_jar_finder.no_issues"), NORMAL_COLOR)
            );
        }
    }

    private static List<Path> discoverArchives() {
        Path modsFolder = ModListUtils.MODS_FOLDER;
        if (!Files.isDirectory(modsFolder)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(modsFolder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isArchive(path.getFileName().toString()))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to enumerate mods folder for CorruptedJarFinder", e);
            return Collections.emptyList();
        }
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    private static boolean isInnerArchiveName(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    private static String toModsRelative(Path jarPath) {
        Path modsFolder = ModListUtils.MODS_FOLDER.toAbsolutePath().normalize();
        Path normalized = jarPath.toAbsolutePath().normalize();
        if (normalized.startsWith(modsFolder)) {
            Path relative = modsFolder.relativize(normalized);
            return relative.toString().replace('\\', '/');
        }
        Path name = normalized.getFileName();
        return name != null ? name.toString() : normalized.toString();
    }

    private void appendResultsForMod(String jarName, List<CorruptionRecord> records, boolean isFirst) {
        if (isFirst) {
            appendStyledText(LanguageProvider.get("gui.analysis.corrupted_jar_finder.found_header") + "\n", NORMAL_COLOR);
        }
        appendStyledText(jarName + "\n", MOD_COLOR);

        for (CorruptionRecord record : records) {
            String containerDisplay = deriveDisplayName(jarName, record.containerId);
            appendStyledText("  - " + containerDisplay + ": " + record.formattedReason() + "\n", ERROR_COLOR);
        }
        appendStyledText("\n", NORMAL_COLOR);
    }

    private static String deriveDisplayName(String rootJar, String containerId) {
        if (containerId.equals(rootJar)) {
            return LanguageProvider.get("gui.analysis.corrupted_jar_finder.root_container");
        }
        String prefix = rootJar + "!/";
        if (containerId.startsWith(prefix)) {
            return containerId.substring(prefix.length());
        }
        return containerId;
    }

    private static void logCorruptions(List<CorruptionRecord> records) {
        for (CorruptionRecord record : records) {
            CrashAssistantApp.LOGGER.warn("[CorruptedJarFinder] {} -> {}", record.containerId, record.formattedReason());
        }
    }

    private static List<CorruptionRecord> inspectJarAndNested(Path jarPath, String topId) {
        List<CorruptionRecord> records = new ArrayList<>();

        if (!Files.exists(jarPath)) {
            records.add(new CorruptionRecord(topId, CorruptionReason.MISSING, null));
            return records;
        }

        if (!Files.isRegularFile(jarPath)) {
            records.add(new CorruptionRecord(topId, CorruptionReason.NOT_A_FILE, null));
            return records;
        }

        try {
            long size = Files.size(jarPath);
            if (size == 0L) {
                records.add(new CorruptionRecord(topId, CorruptionReason.EMPTY, null));
                return records;
            }
        } catch (IOException e) {
            records.add(new CorruptionRecord(topId, CorruptionReason.IO_ERROR, e.getMessage()));
        }

        // Full-path uniqueness for exact duplicate detections only
        Set<String> visited = new LinkedHashSet<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            boolean hasAnyFile = jarFile.stream().anyMatch(e -> !e.isDirectory());
            if (!hasAnyFile) {
                addRecord(records, visited, new CorruptionRecord(topId, CorruptionReason.EMPTY, null));
            }

            // Force CRC/structure checks and recurse on inner archives using full containerId
            List<JarEntry> entries = Collections.list(jarFile.entries());
            for (JarEntry e : entries) {
                if (e.isDirectory()) continue;
                String entryId = topId + "!/" + e.getName();

                try (InputStream is = jarFile.getInputStream(e)) {
                    if (isInnerArchiveName(e.getName())) {
                        byte[] inner = readAllBytes(is); // triggers CRC for this entry
                        if (inner.length == 0 || e.getSize() == 0L) {
                            addRecord(records, visited, new CorruptionRecord(entryId, CorruptionReason.EMPTY, null));
                            continue;
                        }
                        // Validate inner archive via JarFile on a temp file to parse central directory.
                        validateInnerArchiveViaJarFile(entryId, inner, records, visited);
                    } else {
                        drain(is); // force CRC on non-archive entries
                    }
                } catch (ZipException ze) {
                    addRecord(records, visited, new CorruptionRecord(entryId, CorruptionReason.ZIP_ERROR, ze.getMessage()));
                } catch (IOException ioe) {
                    addRecord(records, visited, new CorruptionRecord(entryId, CorruptionReason.IO_ERROR, ioe.getMessage()));
                }
            }
        } catch (ZipException zipException) {
            addRecord(records, visited, new CorruptionRecord(topId, CorruptionReason.ZIP_ERROR, zipException.getMessage()));
        } catch (IOException ioException) {
            addRecord(records, visited, new CorruptionRecord(topId, CorruptionReason.IO_ERROR, ioException.getMessage()));
        }

        return records;
    }

    /**
     * Robustly validate an in-memory inner archive by writing to a temp file and reopening with JarFile
     * (which parses the central directory). Also drains all entries to trigger CRC checks.
     */
    private static void validateInnerArchiveViaJarFile(String containerId,
                                                       byte[] data,
                                                       List<CorruptionRecord> records,
                                                       Set<String> visited) {
        Path tmp = null;
        try {
            String suffix = containerId.toLowerCase(Locale.ROOT).endsWith(".zip") ? ".zip" : ".jar";
            tmp = Files.createTempFile("caf-nested-", suffix);
            try (OutputStream os = Files.newOutputStream(tmp)) {
                os.write(data);
            }

            try (JarFile jf = new JarFile(tmp.toFile())) {
                boolean sawAnyFile = jf.stream().anyMatch(e -> !e.isDirectory());
                if (!sawAnyFile) {
                    addRecord(records, visited, new CorruptionRecord(containerId, CorruptionReason.EMPTY, null));
                }

                List<JarEntry> entries = Collections.list(jf.entries());
                for (JarEntry e : entries) {
                    if (e.isDirectory()) continue;
                    String nextId = containerId + "!/" + e.getName();
                    try (InputStream is = jf.getInputStream(e)) {
                        if (isInnerArchiveName(e.getName())) {
                            byte[] nested = readAllBytes(is); // triggers CRC for the entry payload
                            if (nested.length == 0 || e.getSize() == 0L) {
                                addRecord(records, visited, new CorruptionRecord(nextId, CorruptionReason.EMPTY, null));
                                continue;
                            }
                            // Recurse: always via JarFile on temp to catch central-dir issues at deeper levels
                            validateInnerArchiveViaJarFile(nextId, nested, records, visited);
                        } else {
                            drain(is); // fully consume to verify CRC
                        }
                    } catch (ZipException ze) {
                        addRecord(records, visited, new CorruptionRecord(nextId, CorruptionReason.ZIP_ERROR, ze.getMessage()));
                    } catch (IOException ioe) {
                        addRecord(records, visited, new CorruptionRecord(nextId, CorruptionReason.IO_ERROR, ioe.getMessage()));
                    }
                }
            }
        } catch (ZipException ze) {
            addRecord(records, visited, new CorruptionRecord(containerId, CorruptionReason.ZIP_ERROR, ze.getMessage()));
        } catch (IOException ioe) {
            addRecord(records, visited, new CorruptionRecord(containerId, CorruptionReason.IO_ERROR, ioe.getMessage()));
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignore) {
                }
            }
        }
    }

    private static void addRecord(List<CorruptionRecord> out, Set<String> visited, CorruptionRecord rec) {
        String deDupKey = rec.containerId + "|" + rec.reason.name() + "|" + (rec.detail == null ? "" : rec.detail);
        if (visited.add(deDupKey)) out.add(rec);
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        while (in.read(buf) != -1) { /* consume */ }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int read;
        while ((read = in.read(data)) != -1) {
            buffer.write(data, 0, read);
        }
        return buffer.toByteArray();
    }
}
