package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.CodexAnalysis;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.ErroringEntity;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.OutOfMemoryError;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.win_event.PhysX_64;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.win_event.WasClosedByWindows;
import dev.kostromdan.mods.crash_assistant.app.scripts.AnalysisScriptManager;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LogAnalyser {
    private static final long ANALYSIS_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final List<KnownCrashReason> registeredReasons = new ArrayList<>();
    private static final List<CodexAnalysis> registeredCodexReasons = new ArrayList<>();
    private static final Map<Thread, String> activeWorkers = new ConcurrentHashMap<>();
    private static final Object publicationLock = new Object();
    private static boolean reasonsRegistered = false;
    private static boolean mixinApplyScheduled;
    private static volatile long analysisDeadlineNanos;
    private static volatile boolean analysisCancelled;
    private static boolean timeoutLogged;
    private static volatile ExecutorService activePool;
    private static volatile String activeStage;
    private static volatile Thread coordinatorThread;
    public static final HashSet<LogType> CodexSupportedLogTypes = new HashSet<LogType>() {{
        add(LogType.LOG);
        add(LogType.CRASH_REPORT);
    }};

    public static void registerKnownCrashReason(KnownCrashReason reason) {
        registeredReasons.add(reason);
    }

    public static void registerCodexKnownCrashReason(CodexAnalysis reason) {
        registeredCodexReasons.add(reason);
    }

    public static synchronized void analyseLogs() {
        ensureAnalysisDeadline();
        coordinatorThread = Thread.currentThread();
        try {
            analyseLogsWithinDeadline();
        } finally {
            if (coordinatorThread == Thread.currentThread()) {
                coordinatorThread = null;
            }
        }
    }

    public static synchronized void startInitialAnalysisDeadline() {
        ensureAnalysisDeadline();
    }

    private static void analyseLogsWithinDeadline() {
        if (!CrashAssistantConfig.getBoolean("analysis.enabled")) {
            return;
        }
        if (!canContinue()) {
            if (!analysisCancelled && isAnalysisDeadlineReached()) {
                handleTimeout(null, "analysis startup");
            }
            return;
        }
        long startTime = System.currentTimeMillis();
        registerReasons();
        synchronized (KnownCrashReasonMessage.class) {
            if (!readLogsNeededForAnalysisWithinDeadline()) {
                return;
            }
            if (!runSingleTaskStage("analysis scripts", AnalysisScriptManager::runAnalysisScripts)) {
                return;
            }
            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            setActiveStage(pool, "analysis tasks");
            HashSet<String> disabledCrashReasons = new HashSet<>(CrashAssistantConfig.getBlacklistedAnalysis());
            for (Log log : LogsList.getLogsSnapshot()) {
                if (!canContinue()) {
                    break;
                }
                analyseLog(log, disabledCrashReasons, pool);
            }
            pool.shutdown();
            if (!awaitTermination(pool, "analysis tasks")) {
                return;
            }
            CrashAssistantApp.LOGGER.info("Analysis finished in {} ms", System.currentTimeMillis() - startTime);
        }
    }

    public static synchronized void readLogsNeededForAnalysis() {
        ensureAnalysisDeadline();
        readLogsNeededForAnalysisWithinDeadline();
    }

    private static boolean readLogsNeededForAnalysisWithinDeadline() {
        if (!CrashAssistantConfig.getBoolean("analysis.enabled")) {
            return true;
        }
        if (!canContinue()) {
            if (!analysisCancelled && isAnalysisDeadlineReached()) {
                handleTimeout(null, "reading tasks");
            }
            return false;
        }
        long startTime = System.currentTimeMillis();
        registerReasons();
        synchronized (KnownCrashReasonMessage.class) {
            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            setActiveStage(pool, "reading tasks");
            for (Log log : LogsList.getLogsSnapshot()) {
                if (!canContinue()) {
                    break;
                }
                if (registeredReasons.stream().noneMatch(reason ->
                        reason.getLogTypes().contains(log.getType()))) continue;
                submitTask(pool, "reading " + log.getFileName(), () ->
                        log.getReader().readLogFileSafe()
                );
            }

            pool.shutdown();
            if (!awaitTermination(pool, "reading tasks")) {
                return false;
            }
            for (Log log : LogsList.getLogsSnapshot()) {
                log.getReader().destroyAllLinesCache();
            }
            CrashAssistantApp.LOGGER.info("Reading finished in {} ms", System.currentTimeMillis() - startTime);
            return true;
        }
    }

    private static boolean awaitTermination(ExecutorService pool, String taskDescription) {
        try {
            long remainingNanos = analysisDeadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                handleTimeout(pool, taskDescription);
                return false;
            }
            try {
                if (pool.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                    return !analysisCancelled;
                }
                handleTimeout(pool, taskDescription);
            } catch (InterruptedException e) {
                synchronized (publicationLock) {
                    analysisCancelled = true;
                }
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                CrashAssistantApp.LOGGER.error("Interrupted while awaiting termination of " + taskDescription, e);
            }
            return false;
        } finally {
            clearActiveStage(pool);
        }
    }

    private static boolean runSingleTaskStage(String taskDescription, Runnable task) {
        if (!canContinue()) {
            if (!analysisCancelled && isAnalysisDeadlineReached()) {
                handleTimeout(null, taskDescription);
            }
            return false;
        }
        ExecutorService pool = Executors.newSingleThreadExecutor();
        setActiveStage(pool, taskDescription);
        submitTask(pool, taskDescription, task);
        pool.shutdown();
        return awaitTermination(pool, taskDescription);
    }

    private static void submitTask(ExecutorService pool, String taskDescription, Runnable task) {
        try {
            pool.submit(() -> {
                Thread worker = Thread.currentThread();
                synchronized (publicationLock) {
                    if (!canContinue()) {
                        return;
                    }
                    activeWorkers.put(worker, taskDescription);
                }
                try {
                    if (canContinue()) {
                        task.run();
                    }
                } catch (Throwable throwable) {
                    CrashAssistantApp.LOGGER.error("Error while executing " + taskDescription, throwable);
                } finally {
                    activeWorkers.remove(worker);
                }
            });
        } catch (RejectedExecutionException e) {
            if (!analysisCancelled) {
                CrashAssistantApp.LOGGER.error("Failed to submit " + taskDescription, e);
            }
        }
    }

    private static void handleTimeout(ExecutorService pool, String taskDescription) {
        synchronized (publicationLock) {
            if (!timeoutLogged) {
                String activeWorkerStacks;
                try {
                    activeWorkerStacks = formatActiveWorkerStacks();
                } catch (Throwable throwable) {
                    activeWorkerStacks = "Failed to capture active analysis worker stack traces: " + throwable;
                }
                try {
                    AnalysisScriptManager.finalizeCurrentWarningsAtTimeout();
                } catch (Throwable throwable) {
                    activeWorkerStacks += "\nFailed to finalize partial analysis script results: " + throwable;
                }
                analysisCancelled = true;
                timeoutLogged = true;
                CrashAssistantApp.LOGGER.error(
                        "Log analysis timed out after 60 seconds while awaiting {}. Cancellation was requested for outstanding tasks.\n{}",
                        taskDescription,
                        activeWorkerStacks
                );
            } else {
                analysisCancelled = true;
            }
        }
        if (pool != null) {
            pool.shutdownNow();
        } else if (coordinatorThread != null && coordinatorThread != Thread.currentThread()) {
            coordinatorThread.interrupt();
        }
    }

    private static String formatActiveWorkerStacks() {
        StringBuilder stacks = new StringBuilder();
        int capturedWorkers = 0;
        for (Map.Entry<Thread, String> entry : new ArrayList<>(activeWorkers.entrySet())) {
            Thread worker = entry.getKey();
            if (!worker.isAlive() || !activeWorkers.containsKey(worker)) {
                continue;
            }
            stacks.append('"').append(worker.getName()).append("\" id=").append(worker.getId())
                    .append(" state=").append(worker.getState())
                    .append(" task=").append(entry.getValue()).append('\n');
            for (StackTraceElement element : worker.getStackTrace()) {
                stacks.append("\tat ").append(element).append('\n');
            }
            capturedWorkers++;
        }

        if (capturedWorkers == 0) {
            Thread coordinator = coordinatorThread;
            if (coordinator != null && coordinator.isAlive() && coordinator != Thread.currentThread()) {
                stacks.append('"').append(coordinator.getName()).append("\" id=").append(coordinator.getId())
                        .append(" state=").append(coordinator.getState()).append(" task=analysis coordinator\n");
                for (StackTraceElement element : coordinator.getStackTrace()) {
                    stacks.append("\tat ").append(element).append('\n');
                }
                capturedWorkers++;
            }
        }

        if (capturedWorkers == 0) {
            return "No active analysis threads at timeout.";
        }
        return "Active analysis thread stack traces (" + capturedWorkers + "):\n" + stacks;
    }

    private static void ensureAnalysisDeadline() {
        if (analysisDeadlineNanos == 0) {
            analysisDeadlineNanos = System.nanoTime() + ANALYSIS_TIMEOUT_NANOS;
        }
    }

    private static boolean canContinue() {
        return !isAnalysisStopped();
    }

    public static boolean isAnalysisDeadlineReached() {
        return analysisDeadlineNanos != 0 && System.nanoTime() - analysisDeadlineNanos >= 0;
    }

    public static boolean isAnalysisStopped() {
        return analysisCancelled || isAnalysisDeadlineReached();
    }

    public static void stopTimedOutAnalysisForUpload() {
        if (!isAnalysisDeadlineReached()) {
            return;
        }
        ExecutorService pool = activePool;
        if (pool != null || coordinatorThread != null) {
            String stage = activeStage == null ? "analysis tasks" : activeStage;
            handleTimeout(pool, stage);
            return;
        }
        synchronized (publicationLock) {
            analysisCancelled = true;
        }
    }

    private static void setActiveStage(ExecutorService pool, String taskDescription) {
        activeStage = taskDescription;
        activePool = pool;
    }

    private static void clearActiveStage(ExecutorService pool) {
        if (activePool == pool) {
            activePool = null;
            activeStage = null;
        }
    }

    public static boolean addCrashReasonMessageIfAnalysisActive(KnownCrashReasonMessage message) {
        return replaceCrashReasonMessageIfAnalysisActive(null, message);
    }

    public static boolean replaceCrashReasonMessageIfAnalysisActive(
            KnownCrashReasonMessage previous,
            KnownCrashReasonMessage replacement
    ) {
        return replaceCrashReasonMessageIfAnalysisActive(previous, replacement, null);
    }

    public static boolean replaceCrashReasonMessageIfAnalysisActive(
            KnownCrashReasonMessage previous,
            KnownCrashReasonMessage replacement,
            Runnable afterPublication
    ) {
        synchronized (publicationLock) {
            if (!canContinue()) {
                return false;
            }
            synchronized (KnownCrashReasonMessage.getAllMessages()) {
                if (previous != null) {
                    KnownCrashReasonMessage.getAllMessages().remove(previous);
                }
                KnownCrashReasonMessage.addCrashReasonMessage(replacement);
            }
            if (afterPublication != null) {
                afterPublication.run();
            }
            return true;
        }
    }

    private static synchronized void analyseLog(Log log, HashSet<String> disabledCrashReasons, ExecutorService pool) {
        if (log.isAnalysed()) return;

        List<KnownCrashReason> registeredReasonsForThisLog = registeredReasons.stream()
                .filter(reason ->
                        reason.getLogTypes().contains(log.getType()) &&
                                !disabledCrashReasons.contains(reason.getClass().getSimpleName())
                ).collect(Collectors.toList());

        for (KnownCrashReason reason : registeredReasonsForThisLog) {
            if (!canContinue()) {
                break;
            }
            if (reason instanceof MixinApply) {
                if (mixinApplyScheduled) {
                    continue;
                }
                mixinApplyScheduled = true;
            }
            submitTask(pool, "analysing " + log.getFileName() + " with " + reason.getClass().getSimpleName(), () -> {
                if (reason.matches(log)
//                        || true //dubug too see all available warnings
                ) {
                    addCrashReasonMessageIfAnalysisActive(
                            new KnownCrashReasonMessage(log, reason)
                    );
                }
            });
        }
        log.setAnalysed(true);
    }

    public static synchronized String analyseCodexMessage(String message) {
        registerReasons();
        for (CodexAnalysis reason : registeredCodexReasons) {
            if (reason.matches(message)) {
                return reason.getMessage();
            }
        }
        return "";
    }

    public static synchronized void registerReasons() {
        if (reasonsRegistered) {
            return;
        }
        registerKnownCrashReason(new ConnectorIncompatibleFabricMods());
        registerKnownCrashReason(new MixinApply());
        registerKnownCrashReason(new ModuleFind());
        registerKnownCrashReason(new ModuleResolution());

        registerKnownCrashReason(new AlLibAlcCleanup());
        registerKnownCrashReason(new Atio6axx());
        registerKnownCrashReason(new GPUDriverIssue());
        registerKnownCrashReason(new Ig7icd64());
        registerKnownCrashReason(new InsufficientMemory());
        registerKnownCrashReason(new JavaTooHigh());
        registerKnownCrashReason(new Jemalloc());
        registerKnownCrashReason(new Jvm());
        registerKnownCrashReason(new LibGLFWDotSo());
        registerKnownCrashReason(new LibOpenALDotSo());
        registerKnownCrashReason(new MacJDK());
        registerKnownCrashReason(new MacOSIncompatibleShaderDriverIssue());
        registerKnownCrashReason(new ModernIntelDriverIssue());
        registerKnownCrashReason(new nglMultiDrawElementsBaseVertex());
        registerKnownCrashReason(new Nvoglv64());

        registerKnownCrashReason(new AzureLibAddons());
        registerKnownCrashReason(new CorruptedModJar());
        registerKnownCrashReason(new Create6Addons());
        registerKnownCrashReason(new CtovWithoutLithostitched());
        registerKnownCrashReason(new CurseForgeCorrupted());
        registerKnownCrashReason(new DiskSpaceEnded());
        registerKnownCrashReason(new DuplicatedMods());
        registerKnownCrashReason(new EpicFightAddons());
        registerKnownCrashReason(new FeatureOrderCycle());
        registerKnownCrashReason(new FerriteCoreNeighborTable());
        registerKnownCrashReason(new GeckoLibOculusCompat());
        registerKnownCrashReason(new GroovyModLoaderIPv6());
        registerKnownCrashReason(new IrlandaCoreBackDoor());
        registerKnownCrashReason(new JnaPermissionIssue());
        registerKnownCrashReason(new KubeJSDataPack());
        registerKnownCrashReason(new LanguageProviderMismatch());
        registerKnownCrashReason(new LegacyTooManyIds());
        registerKnownCrashReason(new McdaMcdwVsClumps());
        registerKnownCrashReason(new MedievalOriginsVsForgeOrigins());
        registerKnownCrashReason(new MissingEmbeddiumForOculus());
        registerKnownCrashReason(new MissingIndium());
        registerKnownCrashReason(new MissingUnsupportedDependencies());
        registerKnownCrashReason(new ModernFixWatchDog());
        registerKnownCrashReason(new NeoForgeVersion1_20_1());
        registerKnownCrashReason(new Optifine());
        registerKnownCrashReason(new OutOfMemoryError());
        registerKnownCrashReason(new ResourceLocationException());
        registerKnownCrashReason(new Rubidium());
        registerKnownCrashReason(new ServerConfigCorrupted());
        registerKnownCrashReason(new SimpleCloudsShaders());
        registerKnownCrashReason(new UnsupportedClassVersion());
        registerKnownCrashReason(new UsedByAnotherProcess());
        registerKnownCrashReason(new Version1_21());
        registerKnownCrashReason(new WaterMediaVLCMissing());

        registerKnownCrashReason(new PhysX_64());
        registerKnownCrashReason(new WasClosedByWindows());


        registerCodexKnownCrashReason(new ErroringEntity());

        if (Boot.getStartupWarningsJson() != null) {
            try {
                Type listType = new TypeToken<List<ScriptWarning>>(){}.getType();
                List<ScriptWarning> warnings = new Gson().fromJson(Boot.getStartupWarningsJson(), listType);

                for (ScriptWarning w : warnings) {
                    KnownCrashReason reason = new ScriptedAnalysis(null, w);
                    addCrashReasonMessageIfAnalysisActive(new KnownCrashReasonMessage(null, reason));
                }
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Failed to process startup warnings:", e);
            }
        }

        reasonsRegistered = true;
    }
}
