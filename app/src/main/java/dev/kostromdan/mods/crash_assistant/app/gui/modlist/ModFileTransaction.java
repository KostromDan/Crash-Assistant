package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies a prepared set of mod-file replacements as one rollback-capable operation. */
final class ModFileTransaction {
    interface CancellationCheck {
        boolean isCancelled();
    }

    static final class Installation {
        final Path stagedPath;
        final Path targetPath;
        final Path existingSourceToRemove;

        Installation(Path stagedPath, Path targetPath) {
            this(stagedPath, targetPath, null);
        }

        Installation(Path stagedPath, Path targetPath, Path existingSourceToRemove) {
            this.stagedPath = stagedPath;
            this.targetPath = targetPath;
            this.existingSourceToRemove = existingSourceToRemove;
        }
    }

    static final class Result {
        private final IOException cleanupFailure;
        private final Path backupDirectory;

        private Result(IOException cleanupFailure, Path backupDirectory) {
            this.cleanupFailure = cleanupFailure;
            this.backupDirectory = backupDirectory;
        }

        IOException getCleanupFailure() {
            return cleanupFailure;
        }

        Path getBackupDirectory() {
            return backupDirectory;
        }
    }

    static final class CancelledException extends Exception {
        private final boolean rollbackComplete;
        private final Path backupDirectory;

        private CancelledException(boolean rollbackComplete, Path backupDirectory, IOException rollbackFailure) {
            super("Mod file operation was cancelled", rollbackFailure);
            this.rollbackComplete = rollbackComplete;
            this.backupDirectory = backupDirectory;
        }

        boolean isRollbackComplete() {
            return rollbackComplete;
        }

        Path getBackupDirectory() {
            return backupDirectory;
        }
    }

    static final class TransactionException extends Exception {
        private final boolean rollbackComplete;
        private final Path backupDirectory;

        private TransactionException(Throwable cause, boolean rollbackComplete, Path backupDirectory,
                                     IOException rollbackFailure) {
            super(cause);
            this.rollbackComplete = rollbackComplete;
            this.backupDirectory = backupDirectory;
            if (rollbackFailure != null && rollbackFailure != cause) {
                addSuppressed(rollbackFailure);
            }
        }

        boolean isRollbackComplete() {
            return rollbackComplete;
        }

        Path getBackupDirectory() {
            return backupDirectory;
        }
    }

    private ModFileTransaction() {
    }

    static Result apply(Path backupRoot,
                        Collection<Path> pathsToRemove,
                        Collection<Path> pathsToKeep,
                        List<Installation> installations,
                        CancellationCheck cancellationCheck)
            throws CancelledException, TransactionException {
        if (backupRoot == null) {
            throw new IllegalArgumentException("backupRoot must not be null");
        }

        List<Installation> preparedInstallations = installations == null
                ? Collections.<Installation>emptyList()
                : new ArrayList<Installation>(installations);
        Set<Path> keptPaths = normalize(pathsToKeep);
        for (Path keptPath : keptPaths) {
            ModListUtils.requireDirectModPath(keptPath);
        }
        LinkedHashMap<Path, Path> stagedByTarget = validateInstallations(preparedInstallations);
        LinkedHashSet<Path> touchedPaths = new LinkedHashSet<Path>();
        if (pathsToRemove != null) {
            for (Path path : pathsToRemove) {
                if (path == null) continue;
                Path normalized = normalize(path);
                ModListUtils.requireDirectModPath(normalized);
                if (!keptPaths.contains(normalized)) {
                    touchedPaths.add(normalized);
                }
            }
        }
        for (Installation installation : preparedInstallations) {
            if (installation.existingSourceToRemove == null) continue;
            Path normalized = normalize(installation.existingSourceToRemove);
            ModListUtils.requireDirectModPath(normalized);
            if (!keptPaths.contains(normalized)) {
                touchedPaths.add(normalized);
            }
        }
        touchedPaths.addAll(stagedByTarget.keySet());

        Path backupDirectory;
        try {
            Files.createDirectories(backupRoot);
            backupDirectory = Files.createTempDirectory(backupRoot, ".transaction-");
        } catch (Exception e) {
            throw new TransactionException(e, true, null, null);
        }

        LinkedHashMap<Path, Path> backups = new LinkedHashMap<Path, Path>();
        List<Installation> installed = new ArrayList<Installation>();
        try {
            int backupIndex = 0;
            for (Path original : touchedPaths) {
                checkCancelled(cancellationCheck);
                if (!Files.exists(original)) continue;
                String originalName = original.getFileName() == null
                        ? "file"
                        : original.getFileName().toString();
                Path backup = backupDirectory.resolve(backupIndex++ + "-" + originalName);
                Files.move(original, backup);
                backups.put(original, backup);
            }

            for (Installation installation : preparedInstallations) {
                checkCancelled(cancellationCheck);
                Path target = normalize(installation.targetPath);
                ModListUtils.requireDirectModPath(target);
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.move(installation.stagedPath, target);
                installed.add(new Installation(installation.stagedPath, target));
            }
            checkCancelled(cancellationCheck);
        } catch (CancelledSignal cancelled) {
            IOException rollbackFailure = rollback(installed, backups, backupDirectory);
            throw new CancelledException(rollbackFailure == null, backupDirectory, rollbackFailure);
        } catch (Exception failure) {
            IOException rollbackFailure = rollback(installed, backups, backupDirectory);
            throw new TransactionException(failure, rollbackFailure == null, backupDirectory, rollbackFailure);
        }

        IOException cleanupFailure = deleteBackupDirectory(backupDirectory);
        return new Result(cleanupFailure, cleanupFailure == null ? null : backupDirectory);
    }

    private static LinkedHashMap<Path, Path> validateInstallations(List<Installation> installations)
            throws TransactionException {
        LinkedHashMap<Path, Path> stagedByTarget = new LinkedHashMap<Path, Path>();
        try {
            for (Installation installation : installations) {
                if (installation == null || installation.stagedPath == null || installation.targetPath == null) {
                    throw new IOException("Prepared mod installation has a missing source or destination");
                }
                if (!Files.isRegularFile(installation.stagedPath)) {
                    throw new IOException("Prepared mod file is missing: " + installation.stagedPath);
                }
                Path target = normalize(installation.targetPath);
                ModListUtils.requireDirectModPath(target);
                if (stagedByTarget.put(target, installation.stagedPath) != null) {
                    throw new IOException("Multiple prepared mod files target the same path: " + target);
                }
            }
            return stagedByTarget;
        } catch (Exception e) {
            throw new TransactionException(e, true, null, null);
        }
    }

    private static void checkCancelled(CancellationCheck cancellationCheck) throws CancelledSignal {
        if (cancellationCheck != null && cancellationCheck.isCancelled()) {
            throw new CancelledSignal();
        }
    }

    private static IOException rollback(List<Installation> installed,
                                        LinkedHashMap<Path, Path> backups,
                                        Path backupDirectory) {
        IOException failure = null;
        for (int i = installed.size() - 1; i >= 0; i--) {
            Installation installation = installed.get(i);
            try {
                if (Files.exists(installation.targetPath)) {
                    Path stagedParent = installation.stagedPath.getParent();
                    if (stagedParent != null) {
                        Files.createDirectories(stagedParent);
                    }
                    Files.move(installation.targetPath, installation.stagedPath,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                failure = merge(failure, e);
            }
        }

        List<Map.Entry<Path, Path>> entries = new ArrayList<Map.Entry<Path, Path>>(backups.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<Path, Path> backup = entries.get(i);
            try {
                if (Files.exists(backup.getValue())) {
                    Path originalParent = backup.getKey().getParent();
                    if (originalParent != null) {
                        Files.createDirectories(originalParent);
                    }
                    Files.move(backup.getValue(), backup.getKey(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                failure = merge(failure, e);
            }
        }

        if (failure == null) {
            // The user-visible rollback is complete once every original file is back. A leftover
            // empty transaction directory must not turn a successful rollback into a data warning.
            deleteBackupDirectory(backupDirectory);
        }
        return failure;
    }

    private static IOException deleteBackupDirectory(Path backupDirectory) {
        if (backupDirectory == null) return null;
        IOException failure = null;
        try {
            if (Files.isDirectory(backupDirectory)) {
                try (java.nio.file.DirectoryStream<Path> children = Files.newDirectoryStream(backupDirectory)) {
                    for (Path child : children) {
                        try {
                            Files.deleteIfExists(child);
                        } catch (IOException e) {
                            failure = merge(failure, e);
                        }
                    }
                }
            }
            if (failure == null) {
                Files.deleteIfExists(backupDirectory);
            }
        } catch (IOException e) {
            failure = merge(failure, e);
        }
        return failure;
    }

    private static Set<Path> normalize(Collection<Path> paths) {
        LinkedHashSet<Path> result = new LinkedHashSet<Path>();
        if (paths == null) return result;
        for (Path path : paths) {
            if (path != null) result.add(normalize(path));
        }
        return result;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static IOException merge(IOException first, IOException next) {
        if (first == null) return next;
        if (next != first) first.addSuppressed(next);
        return first;
    }

    private static final class CancelledSignal extends Exception {
    }
}
