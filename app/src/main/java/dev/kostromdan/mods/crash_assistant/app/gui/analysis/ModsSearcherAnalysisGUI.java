package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.FilesRemover;
import dev.kostromdan.mods.crash_assistant.app.utils.MinecraftClassPathHelper;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ModsSearcherAnalysisGUI extends AnalysisGUIBase {
    // Idea from https://github.com/CurseForgeCommunity/Script-Tools
    private static final List<Charset> SEARCH_CHARSETS = Collections.unmodifiableList(Arrays.asList(
            StandardCharsets.ISO_8859_1,
            StandardCharsets.UTF_8
    ));
    private static final Path WORKSPACE_ROOT = Paths.get("").toAbsolutePath().normalize();
    private static final Path TEMP_ROOT = Paths.get("local", "crash_assistant", "mods_searcher_tmp");
    private static final String CONFIG_PATTERNS = "analysis.mods_searcher.patterns";
    private static final String CONFIG_INCLUDE_JAR_IN_JAR = "analysis.mods_searcher.include_jar_in_jar";
    private static final String CONFIG_CASE_INSENSITIVE = "analysis.mods_searcher.case_insensitive";
    private static final String CONFIG_REGEX = "analysis.mods_searcher.regex";
    private static final String CONFIG_CHECK_FILE_NAMES = "analysis.mods_searcher.check_file_names";
    private static final String CONFIG_SEARCH_INSIDE_ARCHIVES = "analysis.mods_searcher.search_inside_archives";
    private static final String CONFIG_INCLUDE_MINECRAFT_CLASSPATH_LIBRARIES = "analysis.mods_searcher.include_minecraft_classpath_libraries";
    private static final String CONFIG_SCOPE = "analysis.mods_searcher.scope";
    private static final String CONFIG_CUSTOM_PATH = "analysis.mods_searcher.custom_path";
    private static final LinkedHashSet<String> ARCHIVE_EXTENSIONS = new LinkedHashSet<>(Arrays.asList(".jar", ".zip"));

    private final SearchOptions options;
    private final TempExtractionManager tempExtractionManager = new TempExtractionManager();
    private final List<FoundResult> foundResults = Collections.synchronizedList(new ArrayList<>());
    private volatile Set<String> classPathDisplayKeys = Collections.emptySet();

    public ModsSearcherAnalysisGUI(JFrame parent, SearchOptions options) {
        super(
                parent,
                LanguageProvider.get("gui.menu.analysis.mods_searcher"),
                LanguageProvider.get("gui.menu.analysis.mods_searcher.desc")
        );
        this.options = options;
        statusLabel.setText(LanguageProvider.get("gui.analysis.mods_searcher.status"));
        currentJarLabel.setText(LanguageProvider.get("gui.analysis.mods_searcher.current_file") + " " + LanguageProvider.get("gui.analysis.none"));
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                tempExtractionManager.cleanup();
            }
        });
    }

    public static void showDialog(JFrame parent) {
        SearchOptions options = SearchOptionsDialog.showDialog(parent);
        if (options != null) {
            startAnalysis(parent, options);
        }
    }

    public static void startImmediateSearch(JFrame parent,
                                            String rawPatterns,
                                            boolean includeJarInJar,
                                            boolean caseInsensitive,
                                            boolean regex,
                                            boolean checkFileNames,
                                            boolean searchInsideArchives,
                                            boolean includeMinecraftClassPathLibraries,
                                            String scopeId,
                                            String customPathText) {
        SearchOptions options = SearchOptions.create(
                rawPatterns,
                includeJarInJar,
                caseInsensitive,
                regex,
                checkFileNames,
                searchInsideArchives,
                includeMinecraftClassPathLibraries,
                SearchScope.fromStored(scopeId),
                customPathText
        );
        startAnalysis(parent, options);
    }

    private static void startAnalysis(JFrame parent, SearchOptions options) {
        new ModsSearcherAnalysisGUI(parent, options).start();
    }

    @Override
    protected void performAnalysis() {
        Path rootPath = options.rootPath;
        boolean rootExists = Files.isDirectory(rootPath);
        if (!rootExists) {
            SwingUtilities.invokeLater(() -> appendStyledText(
                    LanguageProvider.get("gui.analysis.mods_searcher.folder_not_found")
                            .replace("$PATH$", rootPath.toString()) + "\n",
                    ERROR_COLOR
            ));
            if (!options.includeMinecraftClassPathLibraries) {
                return;
            }
        }

        List<Path> filesToScan = discoverFiles(rootPath);
        SwingUtilities.invokeLater(() -> {
            progressBar.setMaximum(Math.max(1, filesToScan.size()));
            renderStreamingHeader();
        });

        if (filesToScan.isEmpty()) {
            SwingUtilities.invokeLater(() -> appendStyledText(
                    LanguageProvider.get("gui.analysis.mods_searcher.no_files")
                            .replace("$PATH$", rootPath.toString()) + "\n",
                    NORMAL_COLOR
            ));
            return;
        }

        AtomicInteger completedTasks = new AtomicInteger(0);
        for (Path file : filesToScan) {
            executor.submit(() -> {
                if (isCancelled) return;

                String display = toDisplayPath(file);
                SwingUtilities.invokeLater(() ->
                        currentJarLabel.setText(LanguageProvider.get("gui.analysis.mods_searcher.current_file") + " " + display)
                );

                try {
                    List<FoundResult> matches = scanFile(file, display);
                    if (!matches.isEmpty()) {
                        foundResults.addAll(matches);
                        List<FoundResult> batch = new ArrayList<>(matches);
                        SwingUtilities.invokeLater(() -> appendPartialResults(batch));
                    }
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.warn("[ModsSearcher] Failed to scan {}: {}", file, ex.getMessage());
                }

                int done = completedTasks.incrementAndGet();
                SwingUtilities.invokeLater(() -> {
                    if (!isCancelled) {
                        progressBar.setValue(done);
                    }
                });
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (isCancelled) {
            return;
        }

        List<FoundResult> sortedResults = getSortedResults();
        SwingUtilities.invokeLater(() -> {
            textPane.setText("");
            renderResults(sortedResults);
        });
    }

    @Override
    protected void addOkButton() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton manageButton = new JButton(LanguageProvider.get("gui.analysis.mods_searcher.manage_results"));
        manageButton.setEnabled(!getSortedResults().isEmpty());
        manageButton.addActionListener(e -> {
            List<FoundResult> results = getSortedResults();
            if (results.isEmpty()) {
                JOptionPane.showMessageDialog(
                        dialog,
                        LanguageProvider.get("gui.analysis.mods_searcher.no_results_to_manage"),
                        LanguageProvider.get("gui.files_remover.title"),
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            List<FilesRemover.CustomEntry> entries = new ArrayList<>();
            for (FoundResult result : results) {
                entries.add(new FilesRemover.CustomEntry(
                        result.rootPath,
                        result.fullDisplayPath,
                        removerDialog -> openFoundResult(result),
                        removerDialog -> deleteFoundResult(result, removerDialog),
                        removerDialog -> revealFoundResult(result)
                ));
            }

            FilesRemover.showDialog(
                    dialog,
                    entries,
                    FilesRemover.Mode.CONFIG,
                    LanguageProvider.get("gui.analysis.mods_searcher.manage_results_desc")
            );
        });
        buttonPanel.add(manageButton);

        JButton okButton = new JButton(LanguageProvider.get("gui.ok"));
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.revalidate();
    }

    private List<Path> discoverFiles(Path rootPath) {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        int rootFilesCount = 0;
        int classPathArchivesCount = 0;
        Set<String> discoveredClassPathKeys = Collections.emptySet();
        if (Files.isDirectory(rootPath)) {
            try (Stream<Path> stream = Files.walk(rootPath)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !isDisabledPath(path))
                        .forEach(files::add);
            } catch (IOException e) {
                CrashAssistantApp.LOGGER.error("[ModsSearcher] Failed to enumerate {}", rootPath, e);
            }
        }
        rootFilesCount = files.size();

        if (options.includeMinecraftClassPathLibraries) {
            List<Path> classPathArchives = MinecraftClassPathHelper.streamCurrentClassPathArchives()
                    .filter(path -> !isDisabledPath(path))
                    .collect(Collectors.toList());
            classPathArchivesCount = classPathArchives.size();
            classPathArchives.forEach(files::add);
            discoveredClassPathKeys = classPathArchives.stream()
                    .map(ModsSearcherAnalysisGUI::toDisplayKey)
                    .collect(Collectors.toSet());
        }
        classPathDisplayKeys = discoveredClassPathKeys;

        List<Path> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.naturalOrder());
        CrashAssistantApp.LOGGER.info(
                "[ModsSearcher] Files discovered. rootFiles={}, classPathArchives={}, total={}, scope={}, includeClassPathLibraries={}, checkFileNames={}, searchInsideArchives={}, includeJarInJar={}, caseInsensitive={}, regex={}",
                rootFilesCount,
                classPathArchivesCount,
                sorted.size(),
                options.scope.id,
                options.includeMinecraftClassPathLibraries,
                options.checkFileNames,
                options.searchInsideArchives,
                options.includeJarInJar,
                options.caseInsensitive,
                options.regex
        );
        return sorted;
    }

    private List<FoundResult> scanFile(Path file, String display) {
        LinkedHashMap<String, FoundResult> matches = new LinkedHashMap<>();

        if (options.checkFileNames && matchesFileName(file.getFileName().toString(), display)) {
            registerMatch(matches, file, display, display, Collections.<String>emptyList(), MatchKind.FILE_NAME);
        }

        if (isArchiveFile(file) && options.searchInsideArchives) {
            scanArchiveFile(file, display, matches);
        } else if (matchesContent(file)) {
            registerMatch(matches, file, display, display, Collections.<String>emptyList(), MatchKind.CONTENT);
        }

        return new ArrayList<>(matches.values());
    }

    private void scanArchiveFile(Path rootPath, String rootDisplay, Map<String, FoundResult> matches) {
        try (ZipFile zipFile = new ZipFile(rootPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                List<String> chain = new ArrayList<>();
                chain.add(entry.getName());
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    byte[] data = readAllBytes(inputStream);
                    handleArchiveEntry(rootPath, rootDisplay, chain, data, matches);
                }
            }
        } catch (Exception ex) {
            CrashAssistantApp.LOGGER.warn("[ModsSearcher] Failed to inspect archive {}: {}", rootPath, ex.getMessage());
        }
    }

    private void handleArchiveEntry(Path rootPath,
                                    String rootDisplay,
                                    List<String> chain,
                                    byte[] data,
                                    Map<String, FoundResult> matches) {
        String entryPath = chain.get(chain.size() - 1);
        if (isDisabledName(entryPath)) {
            return;
        }

        String fullDisplay = buildArchiveDisplay(rootDisplay, chain);
        if (options.checkFileNames && matchesFileName(entryPath, fullDisplay)) {
            registerMatch(matches, rootPath, rootDisplay, fullDisplay, chain, MatchKind.FILE_NAME);
        }

        if (isArchiveName(entryPath)) {
            if (options.includeJarInJar) {
                scanNestedArchive(rootPath, rootDisplay, chain, data, matches);
            }
            return;
        }

        if (matchesContent(data)) {
            registerMatch(matches, rootPath, rootDisplay, fullDisplay, chain, MatchKind.CONTENT);
        }
    }

    private void scanNestedArchive(Path rootPath,
                                   String rootDisplay,
                                   List<String> prefixChain,
                                   byte[] archiveBytes,
                                   Map<String, FoundResult> matches) {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                List<String> nestedChain = new ArrayList<>(prefixChain);
                nestedChain.add(entry.getName());
                byte[] data = readAllBytes(zipInputStream);
                handleArchiveEntry(rootPath, rootDisplay, nestedChain, data, matches);
            }
        } catch (Exception ex) {
            CrashAssistantApp.LOGGER.warn("[ModsSearcher] Failed to inspect nested archive in {}: {}", rootPath, ex.getMessage());
        }
    }

    private void registerMatch(Map<String, FoundResult> matches,
                               Path rootPath,
                               String rootDisplay,
                               String fullDisplay,
                               List<String> archiveChain,
                               MatchKind kind) {
        FoundResult existing = matches.get(fullDisplay);
        if (existing == null) {
            existing = new FoundResult(rootPath, rootDisplay, fullDisplay, archiveChain);
            matches.put(fullDisplay, existing);
        }
        existing.matchKinds.add(kind);
    }

    private boolean matchesFileName(String candidateName, String displayPath) {
        if (!options.checkFileNames) return false;
        if (options.matcher.matches(candidateName)) return true;

        String simpleName = simpleName(candidateName);
        if (!simpleName.equals(candidateName) && options.matcher.matches(simpleName)) return true;

        return options.matcher.matches(displayPath.replace('\\', '/'));
    }

    private boolean matchesContent(Path path) {
        try {
            return matchesContent(Files.readAllBytes(path));
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.warn("[ModsSearcher] Failed to read {}: {}", path, e.getMessage());
            return false;
        }
    }

    private boolean matchesContent(byte[] data) {
        if (data.length == 0) return false;
        for (Charset charset : SEARCH_CHARSETS) {
            if (options.matcher.matches(new String(data, charset))) {
                return true;
            }
        }
        return false;
    }

    private List<FoundResult> getSortedResults() {
        List<FoundResult> copy;
        synchronized (foundResults) {
            copy = new ArrayList<>(foundResults);
        }
        copy.sort(Comparator.comparing((FoundResult r) -> r.rootDisplayPath)
                .thenComparing(r -> r.fullDisplayPath));
        return copy;
    }

    private void renderStreamingHeader() {
        appendStyledText(
                LanguageProvider.get("gui.analysis.mods_searcher.selected_root")
                        .replace("$PATH$", options.rootPath.toString()) + "\n",
                NORMAL_COLOR
        );
        appendStyledText(
                LanguageProvider.get("gui.analysis.mods_searcher.search_terms")
                        .replace("$TERMS$", String.join(", ", options.searchTerms)) + "\n",
                NORMAL_COLOR
        );
    }

    private void appendPartialResults(List<FoundResult> resultsBatch) {
        if (resultsBatch.isEmpty()) {
            return;
        }

        resultsBatch.sort(Comparator.comparing((FoundResult r) -> r.rootDisplayPath)
                .thenComparing(r -> r.fullDisplayPath));

        appendStyledText("\n", NORMAL_COLOR);

        String currentRoot = null;
        for (FoundResult result : resultsBatch) {
            if (!result.rootDisplayPath.equals(currentRoot)) {
                if (currentRoot != null) {
                    appendStyledText("\n", NORMAL_COLOR);
                }
                currentRoot = result.rootDisplayPath;
                appendStyledText(result.rootDisplayPath + "\n", MOD_COLOR);
            }

            appendStyledText("  - " + result.getDisplayInsideRoot() + " ", NORMAL_COLOR);
            appendStyledText("(" + result.getMatchKindsDisplay() + ")\n", ERROR_COLOR);
        }
    }

    private void renderResults(List<FoundResult> sortedResults) {
        appendStyledText(
                LanguageProvider.get("gui.analysis.mods_searcher.selected_root")
                        .replace("$PATH$", options.rootPath.toString()) + "\n",
                NORMAL_COLOR
        );
        appendStyledText(
                LanguageProvider.get("gui.analysis.mods_searcher.search_terms")
                        .replace("$TERMS$", String.join(", ", options.searchTerms)) + "\n\n",
                NORMAL_COLOR
        );

        if (sortedResults.isEmpty()) {
            appendStyledText(LanguageProvider.get("gui.analysis.mods_searcher.not_found") + "\n", NORMAL_COLOR);
            return;
        }

        appendStyledText(
                LanguageProvider.get("gui.analysis.mods_searcher.found_header")
                        .replace("$COUNT$", String.valueOf(sortedResults.size())) + "\n\n",
                NORMAL_COLOR
        );

        String currentRoot = null;
        for (FoundResult result : sortedResults) {
            if (!result.rootDisplayPath.equals(currentRoot)) {
                if (currentRoot != null) {
                    appendStyledText("\n", NORMAL_COLOR);
                }
                currentRoot = result.rootDisplayPath;
                appendStyledText(result.rootDisplayPath + "\n", MOD_COLOR);
            }

            appendStyledText("  - " + result.getDisplayInsideRoot() + " ", NORMAL_COLOR);
            appendStyledText("(" + result.getMatchKindsDisplay() + ")\n", ERROR_COLOR);
        }
    }

    private void openFoundResult(FoundResult result) throws Exception {
        Path pathToOpen = result.isNested() ? tempExtractionManager.extract(result) : result.rootPath;
        if (!Files.exists(pathToOpen)) {
            throw new FileNotFoundException(pathToOpen.toString());
        }
        Desktop.getDesktop().open(pathToOpen.toFile());
    }

    private boolean deleteFoundResult(FoundResult result, Component parent) throws IOException {
        Path pathToDelete = result.rootPath;
        if (!Files.exists(pathToDelete)) {
            return true;
        }

        if (result.isNested()) {
            int option = JOptionPane.showConfirmDialog(
                    parent,
                    LanguageProvider.get("gui.analysis.mods_searcher.delete_nested_confirm")
                            .replace("$PATH$", result.fullDisplayPath)
                            .replace("$ARCHIVE$", result.rootPath.toString()),
                    LanguageProvider.get("gui.analysis.mods_searcher.delete_nested_title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (option != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        Files.deleteIfExists(pathToDelete);
        return true;
    }

    private void revealFoundResult(FoundResult result) throws IOException {
        Path revealPath = result.rootPath;
        if (!Files.exists(revealPath)) return;

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            new ProcessBuilder("explorer.exe", "/select,", revealPath.toAbsolutePath().toString()).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", "-R", revealPath.toAbsolutePath().toString()).start();
        } else {
            Path dir = Files.isDirectory(revealPath) ? revealPath : revealPath.getParent();
            if (dir != null) {
                new ProcessBuilder("xdg-open", dir.toAbsolutePath().toString()).start();
            }
        }
    }

    private String toDisplayPath(Path path) {
        Path absolute = path.toAbsolutePath();
        if (classPathDisplayKeys.contains(toDisplayKey(absolute))) {
            return absolute.toString().replace('\\', '/');
        }
        try {
            return WORKSPACE_ROOT.relativize(absolute).toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return absolute.toString().replace('\\', '/');
        }
    }

    private static String toDisplayKey(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static boolean isArchiveFile(Path path) {
        return isArchiveName(path.getFileName().toString());
    }

    private static boolean isArchiveName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : ARCHIVE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDisabledPath(Path path) {
        return isDisabledName(path.getFileName().toString());
    }

    private static boolean isDisabledName(String name) {
        return simpleName(name).toLowerCase(Locale.ROOT).endsWith(".disabled");
    }

    private static String simpleName(String pathLike) {
        int slash = Math.max(pathLike.lastIndexOf('/'), pathLike.lastIndexOf('\\'));
        return slash >= 0 ? pathLike.substring(slash + 1) : pathLike;
    }

    private static String buildArchiveDisplay(String rootDisplay, List<String> chain) {
        StringBuilder builder = new StringBuilder(rootDisplay);
        for (String segment : chain) {
            builder.append("!/");
            builder.append(segment.replace('\\', '/'));
        }
        return builder.toString();
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private byte[] extractNestedBytes(FoundResult result) throws IOException {
        if (!result.isNested()) {
            return Files.readAllBytes(result.rootPath);
        }
        return extractFromRootArchive(result.rootPath, result.archiveEntryChain, 0);
    }

    private byte[] extractFromRootArchive(Path rootPath, List<String> chain, int index) throws IOException {
        try (ZipFile zipFile = new ZipFile(rootPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(chain.get(index));
            if (entry == null || entry.isDirectory()) {
                throw new FileNotFoundException(chain.get(index));
            }
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                byte[] data = readAllBytes(inputStream);
                if (index == chain.size() - 1) {
                    return data;
                }
                return extractFromNestedArchive(data, chain, index + 1);
            }
        }
    }

    private byte[] extractFromNestedArchive(byte[] archiveData, List<String> chain, int index) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveData))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (!entry.getName().equals(chain.get(index))) {
                    continue;
                }

                byte[] data = readAllBytes(zipInputStream);
                if (index == chain.size() - 1) {
                    return data;
                }
                return extractFromNestedArchive(data, chain, index + 1);
            }
        }
        throw new FileNotFoundException(chain.get(index));
    }

    private enum MatchKind {
        CONTENT("gui.analysis.mods_searcher.match_kind.content"),
        FILE_NAME("gui.analysis.mods_searcher.match_kind.file_name");

        private final String key;

        MatchKind(String key) {
            this.key = key;
        }

        public String getText() {
            return LanguageProvider.get(key);
        }
    }

    private static class FoundResult {
        private final Path rootPath;
        private final String rootDisplayPath;
        private final String fullDisplayPath;
        private final List<String> archiveEntryChain;
        private final EnumSet<MatchKind> matchKinds = EnumSet.noneOf(MatchKind.class);

        private FoundResult(Path rootPath,
                            String rootDisplayPath,
                            String fullDisplayPath,
                            List<String> archiveEntryChain) {
            this.rootPath = rootPath.toAbsolutePath().normalize();
            this.rootDisplayPath = rootDisplayPath;
            this.fullDisplayPath = fullDisplayPath;
            this.archiveEntryChain = Collections.unmodifiableList(new ArrayList<>(archiveEntryChain));
        }

        public boolean isNested() {
            return !archiveEntryChain.isEmpty();
        }

        public String getDisplayInsideRoot() {
            if (!isNested()) {
                return LanguageProvider.get("gui.analysis.mods_searcher.this_file");
            }
            String prefix = rootDisplayPath + "!/";
            if (fullDisplayPath.startsWith(prefix)) {
                return fullDisplayPath.substring(prefix.length());
            }
            return fullDisplayPath;
        }

        public String getMatchKindsDisplay() {
            List<String> parts = new ArrayList<>();
            for (MatchKind kind : matchKinds) {
                parts.add(kind.getText());
            }
            return String.join(", ", parts);
        }
    }

    private static class SearchMatcher {
        private final boolean regex;
        private final boolean caseInsensitive;
        private final List<String> literalTerms;
        private final List<Pattern> regexPatterns;

        private SearchMatcher(List<String> searchTerms, boolean regex, boolean caseInsensitive) {
            this.regex = regex;
            this.caseInsensitive = caseInsensitive;
            this.literalTerms = new ArrayList<>();
            this.regexPatterns = new ArrayList<>();

            if (regex) {
                int flags = Pattern.MULTILINE;
                if (caseInsensitive) {
                    flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                }
                for (String searchTerm : searchTerms) {
                    regexPatterns.add(Pattern.compile(searchTerm, flags));
                }
            } else {
                for (String searchTerm : searchTerms) {
                    literalTerms.add(caseInsensitive ? searchTerm.toLowerCase(Locale.ROOT) : searchTerm);
                }
            }
        }

        public boolean matches(String value) {
            if (value == null || value.isEmpty()) return false;

            if (regex) {
                for (Pattern regexPattern : regexPatterns) {
                    if (regexPattern.matcher(value).find()) {
                        return true;
                    }
                }
                return false;
            }

            String haystack = caseInsensitive ? value.toLowerCase(Locale.ROOT) : value;
            for (String literalTerm : literalTerms) {
                if (haystack.contains(literalTerm)) {
                    return true;
                }
            }
            return false;
        }
    }

    private enum SearchScope {
        ROOT("root", "gui.analysis.mods_searcher.scope.root"),
        MODS("mods", "gui.analysis.mods_searcher.scope.mods"),
        CONFIG("config", "gui.analysis.mods_searcher.scope.config"),
        CUSTOM("custom", "gui.analysis.mods_searcher.scope.custom");

        private final String id;
        private final String labelKey;

        SearchScope(String id, String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
        }

        public String getLabel() {
            return LanguageProvider.get(labelKey);
        }

        public static SearchScope fromStored(String stored) {
            for (SearchScope scope : values()) {
                if (scope.id.equalsIgnoreCase(stored)) {
                    return scope;
                }
            }
            return ROOT;
        }
    }

    private static class SearchOptions {
        private final List<String> searchTerms;
        private final SearchMatcher matcher;
        private final boolean includeJarInJar;
        private final boolean caseInsensitive;
        private final boolean regex;
        private final boolean checkFileNames;
        private final boolean searchInsideArchives;
        private final boolean includeMinecraftClassPathLibraries;
        private final SearchScope scope;
        private final Path rootPath;
        private final String customPathText;
        private final String rawPatterns;

        private SearchOptions(String rawPatterns,
                              List<String> searchTerms,
                              boolean includeJarInJar,
                              boolean caseInsensitive,
                              boolean regex,
                              boolean checkFileNames,
                              boolean searchInsideArchives,
                              boolean includeMinecraftClassPathLibraries,
                              SearchScope scope,
                              String customPathText) {
            this.rawPatterns = rawPatterns;
            this.searchTerms = Collections.unmodifiableList(new ArrayList<>(searchTerms));
            this.includeJarInJar = includeJarInJar;
            this.caseInsensitive = caseInsensitive;
            this.regex = regex;
            this.checkFileNames = checkFileNames;
            this.searchInsideArchives = searchInsideArchives;
            this.includeMinecraftClassPathLibraries = includeMinecraftClassPathLibraries;
            this.scope = scope;
            this.customPathText = customPathText == null ? "" : customPathText.trim();
            this.rootPath = resolveRoot(scope, this.customPathText);
            this.matcher = new SearchMatcher(this.searchTerms, regex, caseInsensitive);
        }

        private static SearchOptions create(String rawPatterns,
                                            boolean includeJarInJar,
                                            boolean caseInsensitive,
                                            boolean regex,
                                            boolean checkFileNames,
                                            boolean searchInsideArchives,
                                            boolean includeMinecraftClassPathLibraries,
                                            SearchScope scope,
                                            String customPathText) {
            String normalizedPatterns = rawPatterns == null ? "" : rawPatterns.replace("\r\n", "\n").trim();
            List<String> terms = Arrays.stream(normalizedPatterns.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (terms.isEmpty()) {
                throw new IllegalArgumentException(LanguageProvider.get("gui.analysis.mods_searcher.validation.no_patterns"));
            }
            if (scope == SearchScope.CUSTOM) {
                if (customPathText == null || customPathText.trim().isEmpty()) {
                    throw new IllegalArgumentException(LanguageProvider.get("gui.analysis.mods_searcher.validation.custom_path_required"));
                }
                Path customPath;
                try {
                    customPath = Paths.get(customPathText.trim()).toAbsolutePath().normalize();
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            LanguageProvider.get("gui.analysis.mods_searcher.validation.custom_path_invalid")
                                    .replace("$PATH$", customPathText.trim()),
                            ex
                    );
                }
                if (!Files.isDirectory(customPath)) {
                    throw new IllegalArgumentException(
                            LanguageProvider.get("gui.analysis.mods_searcher.validation.custom_path_invalid")
                                    .replace("$PATH$", customPath.toString())
                    );
                }
            }

            try {
                return new SearchOptions(
                        normalizedPatterns,
                        terms,
                        includeJarInJar,
                        caseInsensitive,
                        regex,
                        checkFileNames,
                        searchInsideArchives,
                        includeMinecraftClassPathLibraries,
                        scope,
                        customPathText
                );
            } catch (PatternSyntaxException ex) {
                throw new IllegalArgumentException(
                        LanguageProvider.get("gui.analysis.mods_searcher.validation.invalid_regex")
                                .replace("$ERROR$", ex.getMessage()),
                        ex
                );
            }
        }

        private static Path resolveRoot(SearchScope scope, String customPathText) {
            if (scope == SearchScope.MODS) {
                return ModListUtils.MODS_FOLDER.toAbsolutePath().normalize();
            }
            if (scope == SearchScope.CONFIG) {
                return Paths.get("config").toAbsolutePath().normalize();
            }
            if (scope == SearchScope.CUSTOM) {
                return Paths.get(customPathText).toAbsolutePath().normalize();
            }
            return WORKSPACE_ROOT;
        }
    }

    private class TempExtractionManager {
        private final Path sessionDir = TEMP_ROOT.resolve("session-" + UUID.randomUUID());
        private final Map<String, Path> extractedFiles = new LinkedHashMap<>();

        private synchronized Path extract(FoundResult result) throws IOException {
            Path existing = extractedFiles.get(result.fullDisplayPath);
            if (existing != null && Files.exists(existing)) {
                return existing;
            }

            Files.createDirectories(sessionDir);
            byte[] data = extractNestedBytes(result);
            String outputName = simpleName(result.archiveEntryChain.get(result.archiveEntryChain.size() - 1));
            if (outputName.isEmpty()) {
                outputName = "result.bin";
            }

            Path itemDir = sessionDir.resolve(UUID.randomUUID().toString());
            Files.createDirectories(itemDir);
            Path out = itemDir.resolve(outputName);
            Files.write(out, data);
            extractedFiles.put(result.fullDisplayPath, out);
            return out;
        }

        private synchronized void cleanup() {
            if (!Files.exists(sessionDir)) {
                return;
            }

            try (Stream<Path> walk = Files.walk(sessionDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }

    private static class SearchOptionsDialog extends JDialog {
        private SearchOptions result;
        private final JTextArea patternsArea;
        private final JCheckBox includeJarInJarCheckbox;
        private final JCheckBox caseInsensitiveCheckbox;
        private final JRadioButton stringMatchRadio;
        private final JRadioButton regexMatchRadio;
        private final JCheckBox checkFileNamesCheckbox;
        private final JCheckBox searchInsideArchivesCheckbox;
        private final JCheckBox includeMinecraftClassPathLibrariesCheckbox;
        private final JRadioButton rootRadio;
        private final JRadioButton modsRadio;
        private final JRadioButton configRadio;
        private final JRadioButton customRadio;
        private final JPanel customPathPanel;
        private final JTextField customPathField;

        private SearchOptionsDialog(JFrame parent) {
            super((Window) parent, LanguageProvider.get("gui.analysis.mods_searcher.options.title"), Dialog.ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout(10, 10));
            CrashAssistantGUI.setUpIcon(this);

            SearchScope initialScope = SearchScope.fromStored(getStoredString(CONFIG_SCOPE, SearchScope.ROOT.id));
            String initialPatterns = getStoredString(CONFIG_PATTERNS, "");
            String initialCustomPath = getStoredString(CONFIG_CUSTOM_PATH, WORKSPACE_ROOT.toString());

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            JLabel introLabel = new JLabel("<html>" + LanguageProvider.get("gui.analysis.mods_searcher.options.intro").replace("\n", "<br>") + "</html>");
            introLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(introLabel);
            content.add(Box.createVerticalStrut(8));

            JLabel patternsLabel = new JLabel(LanguageProvider.get("gui.analysis.mods_searcher.options.patterns"));
            patternsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(patternsLabel);

            patternsArea = new JTextArea(initialPatterns, 6, 60);
            patternsArea.setLineWrap(false);
            JScrollPane patternsScroll = new JScrollPane(patternsArea);
            patternsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(patternsScroll);
            content.add(Box.createVerticalStrut(8));

            JPanel settingsRow = new JPanel(new GridLayout(1, 2, 10, 0));
            settingsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel optionsGroupPanel = new JPanel(new BorderLayout(0, 8));
            optionsGroupPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEtchedBorder(),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            JLabel optionsLabel = new JLabel(LanguageProvider.get("gui.analysis.mods_searcher.options.search_options"));
            optionsGroupPanel.add(optionsLabel, BorderLayout.NORTH);

            JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 0, 4));
            includeJarInJarCheckbox = new JCheckBox(
                    LanguageProvider.get("gui.analysis.mods_searcher.option.include_jar_in_jar"),
                    getStoredBoolean(CONFIG_INCLUDE_JAR_IN_JAR, true)
            );
            caseInsensitiveCheckbox = new JCheckBox(
                    LanguageProvider.get("gui.analysis.mods_searcher.option.case_insensitive"),
                    getStoredBoolean(CONFIG_CASE_INSENSITIVE, false)
            );
            boolean regexSelected = getStoredBoolean(CONFIG_REGEX, false);
            stringMatchRadio = new JRadioButton(
                    LanguageProvider.get("gui.analysis.mods_searcher.match_mode.string"),
                    !regexSelected
            );
            regexMatchRadio = new JRadioButton(
                    LanguageProvider.get("gui.analysis.mods_searcher.match_mode.regex"),
                    regexSelected
            );
            ButtonGroup matchModeGroup = new ButtonGroup();
            matchModeGroup.add(stringMatchRadio);
            matchModeGroup.add(regexMatchRadio);
            checkFileNamesCheckbox = new JCheckBox(
                    LanguageProvider.get("gui.analysis.mods_searcher.option.check_file_names"),
                    getStoredBoolean(CONFIG_CHECK_FILE_NAMES, true)
            );
            searchInsideArchivesCheckbox = new JCheckBox(
                    LanguageProvider.get("gui.analysis.mods_searcher.option.search_inside_archives"),
                    getStoredBoolean(CONFIG_SEARCH_INSIDE_ARCHIVES, true)
            );
            includeMinecraftClassPathLibrariesCheckbox = new JCheckBox(
                    LanguageProvider.get("gui.analysis.mods_searcher.option.include_minecraft_classpath_libraries"),
                    getStoredBoolean(CONFIG_INCLUDE_MINECRAFT_CLASSPATH_LIBRARIES, true)
            );

            JPanel matchModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            matchModePanel.add(new JLabel(LanguageProvider.get("gui.analysis.mods_searcher.options.match_by") + " "));
            matchModePanel.add(stringMatchRadio);
            matchModePanel.add(Box.createHorizontalStrut(12));
            matchModePanel.add(regexMatchRadio);

            optionsPanel.add(caseInsensitiveCheckbox);
            optionsPanel.add(matchModePanel);
            optionsPanel.add(checkFileNamesCheckbox);
            optionsPanel.add(searchInsideArchivesCheckbox);
            optionsPanel.add(includeJarInJarCheckbox);
            optionsGroupPanel.add(optionsPanel, BorderLayout.CENTER);
            settingsRow.add(optionsGroupPanel);

            JPanel scopeGroupPanel = new JPanel();
            scopeGroupPanel.setLayout(new BoxLayout(scopeGroupPanel, BoxLayout.Y_AXIS));
            scopeGroupPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEtchedBorder(),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            JLabel scopeLabel = new JLabel(LanguageProvider.get("gui.analysis.mods_searcher.options.scope"));
            scopeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            scopeGroupPanel.add(scopeLabel);
            scopeGroupPanel.add(Box.createVerticalStrut(8));

            rootRadio = new JRadioButton(SearchScope.ROOT.getLabel(), initialScope == SearchScope.ROOT);
            modsRadio = new JRadioButton(SearchScope.MODS.getLabel(), initialScope == SearchScope.MODS);
            configRadio = new JRadioButton(SearchScope.CONFIG.getLabel(), initialScope == SearchScope.CONFIG);
            customRadio = new JRadioButton(SearchScope.CUSTOM.getLabel(), initialScope == SearchScope.CUSTOM);

            ButtonGroup group = new ButtonGroup();
            group.add(rootRadio);
            group.add(modsRadio);
            group.add(configRadio);
            group.add(customRadio);

            JPanel scopePanel = new JPanel(new GridLayout(0, 1, 0, 4));
            scopePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            scopePanel.add(rootRadio);
            scopePanel.add(modsRadio);
            scopePanel.add(configRadio);
            scopePanel.add(customRadio);
            scopeGroupPanel.add(scopePanel);
            scopeGroupPanel.add(Box.createVerticalStrut(8));

            customPathField = new JTextField(initialCustomPath, 45);
            JButton browseButton = new JButton(LanguageProvider.get("gui.analysis.mods_searcher.options.browse"));
            browseButton.addActionListener(e -> chooseCustomFolder());

            customPathPanel = new JPanel(new BorderLayout(8, 0));
            customPathPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            customPathPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, customPathField.getPreferredSize().height));
            customPathPanel.add(customPathField, BorderLayout.CENTER);
            customPathPanel.add(browseButton, BorderLayout.EAST);
            scopeGroupPanel.add(customPathPanel);
            scopeGroupPanel.add(Box.createVerticalStrut(8));
            includeMinecraftClassPathLibrariesCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
            scopeGroupPanel.add(includeMinecraftClassPathLibrariesCheckbox);
            settingsRow.add(scopeGroupPanel);

            content.add(settingsRow);

            rootRadio.addActionListener(e -> updateCustomPathPanel());
            modsRadio.addActionListener(e -> updateCustomPathPanel());
            configRadio.addActionListener(e -> updateCustomPathPanel());
            customRadio.addActionListener(e -> updateCustomPathPanel());
            searchInsideArchivesCheckbox.addActionListener(e -> updateArchiveOptionsState());
            updateCustomPathPanel();
            updateArchiveOptionsState();

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton startButton = new JButton(LanguageProvider.get("gui.analysis.mods_searcher.options.start"));
            JButton closeButton = new JButton(LanguageProvider.get("gui.close"));
            startButton.addActionListener(e -> onStart());
            closeButton.addActionListener(e -> dispose());
            buttons.add(startButton);
            buttons.add(closeButton);

            add(content, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(startButton);
            pack();
            setMinimumSize(new Dimension(700, 520));
            setLocationRelativeTo(parent);
        }

        private void chooseCustomFolder() {
            String currentPath = customPathField.getText().trim();
            java.io.File initialDir;
            try {
                initialDir = currentPath.isEmpty()
                        ? WORKSPACE_ROOT.toFile()
                        : Paths.get(currentPath).toFile();
            } catch (Exception ex) {
                initialDir = WORKSPACE_ROOT.toFile();
            }
            JFileChooser chooser = new JFileChooser(initialDir);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle(LanguageProvider.get("gui.analysis.mods_searcher.options.choose_folder"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                customPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }

        private void updateCustomPathPanel() {
            customPathPanel.setVisible(customRadio.isSelected());
            pack();
        }

        private void updateArchiveOptionsState() {
            includeJarInJarCheckbox.setEnabled(searchInsideArchivesCheckbox.isSelected());
        }

        private void onStart() {
            SearchScope scope = getSelectedScope();
            try {
                SearchOptions options = SearchOptions.create(
                        patternsArea.getText(),
                        includeJarInJarCheckbox.isSelected(),
                        caseInsensitiveCheckbox.isSelected(),
                        regexMatchRadio.isSelected(),
                        checkFileNamesCheckbox.isSelected(),
                        searchInsideArchivesCheckbox.isSelected(),
                        includeMinecraftClassPathLibrariesCheckbox.isSelected(),
                        scope,
                        customPathField.getText()
                );
                saveOptions(options);
                result = options;
                dispose();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        LanguageProvider.get("gui.analysis.mods_searcher.options.validation_title"),
                        JOptionPane.WARNING_MESSAGE
                );
            }
        }

        private SearchScope getSelectedScope() {
            if (modsRadio.isSelected()) return SearchScope.MODS;
            if (configRadio.isSelected()) return SearchScope.CONFIG;
            if (customRadio.isSelected()) return SearchScope.CUSTOM;
            return SearchScope.ROOT;
        }

        private static boolean getStoredBoolean(String key, boolean fallback) {
            Object value = CrashAssistantLocalConfig.get(key);
            return value instanceof Boolean ? (Boolean) value : fallback;
        }

        private static String getStoredString(String key, String fallback) {
            Object value = CrashAssistantLocalConfig.get(key);
            return value instanceof String ? (String) value : fallback;
        }

        private static void saveOptions(SearchOptions options) {
            CrashAssistantLocalConfig.set(CONFIG_PATTERNS, options.rawPatterns);
            CrashAssistantLocalConfig.set(CONFIG_INCLUDE_JAR_IN_JAR, options.includeJarInJar);
            CrashAssistantLocalConfig.set(CONFIG_CASE_INSENSITIVE, options.caseInsensitive);
            CrashAssistantLocalConfig.set(CONFIG_REGEX, options.regex);
            CrashAssistantLocalConfig.set(CONFIG_CHECK_FILE_NAMES, options.checkFileNames);
            CrashAssistantLocalConfig.set(CONFIG_SEARCH_INSIDE_ARCHIVES, options.searchInsideArchives);
            CrashAssistantLocalConfig.set(CONFIG_INCLUDE_MINECRAFT_CLASSPATH_LIBRARIES, options.includeMinecraftClassPathLibraries);
            CrashAssistantLocalConfig.set(CONFIG_SCOPE, options.scope.id);
            CrashAssistantLocalConfig.set(CONFIG_CUSTOM_PATH, options.customPathText);
        }

        public static SearchOptions showDialog(JFrame parent) {
            SearchOptionsDialog dialog = new SearchOptionsDialog(parent);
            dialog.setVisible(true);
            return dialog.result;
        }
    }
}
