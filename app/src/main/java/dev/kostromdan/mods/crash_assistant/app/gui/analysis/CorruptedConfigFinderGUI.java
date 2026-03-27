package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import com.electronwill.nightconfig.core.io.ParsingException;
import com.google.gson.JsonParseException;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.FilesRemover;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.config.ConfigChecker;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.config.ConfigCheckerRegistry;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class CorruptedConfigFinderGUI extends AnalysisGUIBase {

    private static final String ERROR_PLACEHOLDER = "$ERROR$";

    private enum CorruptionReason {
        EMPTY("gui.analysis.corrupted_config_finder.reason.empty", false),
        INVALID_TOML("gui.analysis.corrupted_config_finder.reason.invalid_toml", true),
        INVALID_JSON("gui.analysis.corrupted_config_finder.reason.invalid_json", true),
        INVALID_PROPERTIES("gui.analysis.corrupted_config_finder.reason.invalid_properties", true),
        IO_ERROR("gui.analysis.corrupted_config_finder.reason.io_error", true);

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
        final Path path;
        final String displayName;
        final CorruptionReason reason;
        final String detail;

        CorruptionRecord(Path path, String displayName, CorruptionReason reason, String detail) {
            this.path = path;
            this.displayName = displayName;
            this.reason = reason;
            this.detail = detail;
        }

        String formattedReason() {
            return reason.format(detail);
        }
    }

    private static final Path WORKSPACE_ROOT = Paths.get("").toAbsolutePath().normalize();
    private static final Set<String> TOML_EXTENSIONS = new LinkedHashSet<>(Arrays.asList("toml"));
    private static final Set<String> PROPERTIES_EXTENSIONS = new LinkedHashSet<>(Arrays.asList("properties"));
    private static final Set<String> SUPPORTED_EXTENSIONS = ConfigCheckerRegistry.getSupportedExtensions();

    private final Map<String, Path> configsForRemoval = new LinkedHashMap<>();

    public CorruptedConfigFinderGUI(JFrame parent) {
        super(
                parent,
                LanguageProvider.get("gui.menu.analysis.corrupted_config_finder"),
                LanguageProvider.get("gui.analysis.corrupted_config_finder.header")
        );
        statusLabel.setText(LanguageProvider.get("gui.analysis.corrupted_config_finder.status"));
        currentJarLabel.setText(LanguageProvider.get("gui.analysis.corrupted_config_finder.current_file") + " " + LanguageProvider.get("gui.analysis.none"));
    }

    public static void showDialog(JFrame parent) {
        new CorruptedConfigFinderGUI(parent).start();
    }

    @Override
    protected void performAnalysis() {
        List<Path> configFiles = discoverConfigFiles();
        int totalFiles = configFiles.size();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicBoolean headerShown = new AtomicBoolean(false);
        AtomicBoolean anyCorruptionFound = new AtomicBoolean(false);

        SwingUtilities.invokeLater(() -> progressBar.setMaximum(Math.max(1, totalFiles)));

        for (Path configFile : configFiles) {
            executor.submit(() -> {
                if (isCancelled) return;

                String displayName = toDisplayPath(configFile);
                CrashAssistantApp.LOGGER.info("[CorruptedConfigFinder] Checking {}", displayName);

                SwingUtilities.invokeLater(() ->
                        currentJarLabel.setText(LanguageProvider.get("gui.analysis.corrupted_config_finder.current_file") + " " + displayName)
                );

                CorruptionRecord record = inspectConfigFile(configFile, displayName);

                if (record != null) {
                    registerConfigForRemoval(record.displayName, record.path);
                    anyCorruptionFound.set(true);
                    logCorruption(record);

                    SwingUtilities.invokeLater(() -> {
                        boolean first = headerShown.compareAndSet(false, true);
                        appendRecord(record, first);
                    });
                }

                int done = completed.incrementAndGet();
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

        if (!isCancelled && !anyCorruptionFound.get()) {
            SwingUtilities.invokeLater(() ->
                    appendStyledText(LanguageProvider.get("gui.analysis.corrupted_config_finder.no_issues"), NORMAL_COLOR)
            );
        }
    }

    @Override
    protected void addOkButton() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton detectedConfigsButton = new JButton(LanguageProvider.get("gui.analysis.corrupted_config_finder.remover_button"));
        detectedConfigsButton.setEnabled(hasConfigsForRemoval());
        detectedConfigsButton.addActionListener(e -> {
            Map<String, Path> map = buildConfigsRemovalMap();
            if (map.isEmpty()) {
                JOptionPane.showMessageDialog(
                        dialog,
                        LanguageProvider.get("gui.analysis.corrupted_config_finder.no_detected_configs"),
                        LanguageProvider.get("gui.files_remover.title"),
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }
            FilesRemover.showDialog(dialog, map, FilesRemover.Mode.CONFIG);
        });
        buttonPanel.add(detectedConfigsButton);

        String whyButtonKey = getWhyButtonTextKey();
        if (whyButtonKey != null) {
            JButton whyButton = new JButton(LanguageProvider.get(whyButtonKey));
            whyButton.addActionListener(e -> {
                String titleKey = getWhyDialogTitleKey();
                String bodyKey = getWhyDialogBodyKey();
                String title = titleKey != null ? LanguageProvider.get(titleKey) : "";
                String body = bodyKey != null ? LanguageProvider.get(bodyKey) : "";
                int width = getWhyDialogWidth();
                JEditorPane infoPane = CrashAssistantGUI.getEditorPane(body, true, width);
                JOptionPane.showMessageDialog(
                        dialog,
                        infoPane,
                        title,
                        JOptionPane.INFORMATION_MESSAGE
                );
            });
            buttonPanel.add(whyButton);
        }

        JButton okButton = new JButton(LanguageProvider.get("gui.ok"));
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.revalidate();
    }

    private boolean hasConfigsForRemoval() {
        synchronized (configsForRemoval) {
            return !configsForRemoval.isEmpty();
        }
    }

    private Map<String, Path> buildConfigsRemovalMap() {
        LinkedHashMap<String, Path> copy = new LinkedHashMap<>();
        synchronized (configsForRemoval) {
            configsForRemoval.forEach((display, path) -> copy.put(display, path));
        }
        return copy;
    }

    private void registerConfigForRemoval(String displayName, Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        synchronized (configsForRemoval) {
            configsForRemoval.putIfAbsent(displayName, absolute);
        }
    }

    private void appendRecord(CorruptionRecord record, boolean isFirst) {
        if (isFirst) {
            appendStyledText(LanguageProvider.get("gui.analysis.corrupted_config_finder.found_header") + "\n", NORMAL_COLOR);
        }
        appendStyledText(record.displayName + "\n", MOD_COLOR);
        appendStyledText("  - " + record.formattedReason() + "\n\n", ERROR_COLOR);
    }

    private void logCorruption(CorruptionRecord record) {
        CrashAssistantApp.LOGGER.warn("[CorruptedConfigFinder] {} -> {}", record.displayName, record.formattedReason());
    }

    private CorruptionRecord inspectConfigFile(Path path, String displayName) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        String extension = getExtension(path);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            return null;
        }

        try {
            if (Files.size(path) == 0L) {
                return new CorruptionRecord(path, displayName, CorruptionReason.EMPTY, null);
            }
        } catch (IOException ioe) {
            return new CorruptionRecord(path, displayName, CorruptionReason.IO_ERROR, safeMessage(ioe));
        }

        List<ConfigChecker> checkers = ConfigCheckerRegistry.getCheckers(extension);
        if (checkers.isEmpty()) {
            CrashAssistantApp.LOGGER.warn("[CorruptedConfigFinder] No checkers registered for extension '{}'", extension);
            return null;
        }

        List<String> parsingErrors = new ArrayList<>();
        List<String> ioErrors = new ArrayList<>();

        for (ConfigChecker checker : checkers) {
            try {
                checker.check(path);
                CrashAssistantApp.LOGGER.info("[CorruptedConfigFinder] {} validated by {}", displayName, checker.getName());
                return null;
            } catch (IOException ioe) {
                ioErrors.add(formatCheckerFailure(checker, ioe));
                CrashAssistantApp.LOGGER.warn("[CorruptedConfigFinder] {} failed {} due to IO error: {}", displayName, checker.getName(), safeMessage(ioe));
            } catch (ParsingException | JsonParseException pe) {
                parsingErrors.add(formatCheckerFailure(checker, pe));
                CrashAssistantApp.LOGGER.warn("[CorruptedConfigFinder] {} failed {} due to parsing error: {}", displayName, checker.getName(), safeMessage(pe));
            } catch (Exception ex) {
                parsingErrors.add(formatCheckerFailure(checker, ex));
                CrashAssistantApp.LOGGER.warn("[CorruptedConfigFinder] {} failed {}: {}", displayName, checker.getName(), safeMessage(ex));
            }
        }

        if (!ioErrors.isEmpty()) {
            String detail = String.join("\n  - ", ioErrors);
            return new CorruptionRecord(path, displayName, CorruptionReason.IO_ERROR, detail);
        }

        String detail = parsingErrors.isEmpty() ? null : String.join("\n  - ", parsingErrors);
        CorruptionReason reason;
        if (TOML_EXTENSIONS.contains(extension)) {
            reason = CorruptionReason.INVALID_TOML;
        } else if (PROPERTIES_EXTENSIONS.contains(extension)) {
            reason = CorruptionReason.INVALID_PROPERTIES;
        } else {
            reason = CorruptionReason.INVALID_JSON;
        }

        return new CorruptionRecord(path, displayName, reason, detail);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "";
        String message = throwable.getMessage();
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    private static String formatCheckerFailure(ConfigChecker checker, Exception exception) {
        return checker.getName() + ": " + safeMessage(exception);
    }

    private static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String toDisplayPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            Path relative = WORKSPACE_ROOT.relativize(absolute);
            return relative.toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return absolute.toString().replace('\\', '/');
        }
    }

    private static List<Path> discoverConfigFiles() {
        LinkedHashSet<Path> found = new LinkedHashSet<>();

        collectFiles(Paths.get("config"), found);
        collectFiles(Paths.get("defaultconfigs"), found);
        collectServerConfigFiles(Paths.get("saves"), found);

        return new ArrayList<>(found);
    }

    private static void collectFiles(Path root, Set<Path> out) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p))
                    .filter(path -> SUPPORTED_EXTENSIONS.contains(getExtension(path)))
                    .forEach(p -> out.add(p));
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to enumerate configs under {}", root, e);
        }
    }

    private static void collectServerConfigFiles(Path savesRoot, Set<Path> out) {
        if (!Files.isDirectory(savesRoot)) {
            return;
        }
        try (Stream<Path> worlds = Files.list(savesRoot)) {
            worlds.filter(p -> Files.isDirectory(p))
                    .map(world -> world.resolve("serverconfig"))
                    .filter(p -> Files.isDirectory(p))
                    .forEach(serverConfigDir -> collectFiles(serverConfigDir, out));
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Failed to enumerate saves for CorruptedConfigFinder", e);
        }
    }
}
