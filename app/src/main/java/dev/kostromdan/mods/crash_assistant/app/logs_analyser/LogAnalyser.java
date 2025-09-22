package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.CodexAnalysis;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.ErroringEntity;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.OutOfMemoryError;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.win_event.WasClosedByWindows;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LogAnalyser {
    private static final List<KnownCrashReason> registeredReasons = new ArrayList<>();
    private static final List<CodexAnalysis> registeredCodexReasons = new ArrayList<>();
    private static boolean reasonsRegistered = false;
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
        if (!CrashAssistantConfig.getBoolean("analysis.enabled")) {
            return;
        }
        long startTime = System.currentTimeMillis();
        registerReasons();
        readLogsNeededForAnalysis();
        synchronized (KnownCrashReasonMessage.class) {
            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            HashSet<String> disabledCrashReasons = new HashSet<>(CrashAssistantConfig.getBlacklistedAnalysis());
            for (Log log : LogsList.getLogs()) {
                analyseLog(log, disabledCrashReasons, pool);
            }
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CrashAssistantApp.LOGGER.error("Interrupted while awaiting termination of analysis tasks", e);
            }
            CrashAssistantApp.LOGGER.info("Analysis finished in {} ms", System.currentTimeMillis() - startTime);
        }
    }

    public static synchronized void readLogsNeededForAnalysis() {
        if (!CrashAssistantConfig.getBoolean("analysis.enabled")) {
            return;
        }
        long startTime = System.currentTimeMillis();
        registerReasons();
        synchronized (KnownCrashReasonMessage.class) {
            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            for (Log log : LogsList.getLogs()) {
                if (registeredReasons.stream().noneMatch(reason ->
                        reason.getLogTypes().contains(log.getType()))) continue;
                pool.submit(() -> {
                    log.getReader().readLogFileSafe();
                });
            }

            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CrashAssistantApp.LOGGER.error("Interrupted while awaiting termination of reading tasks", e);
            }
            for (Log log : LogsList.getLogs()) {
                log.getReader().destroyAllLinesCache();
            }
            CrashAssistantApp.LOGGER.info("Reading finished in {} ms", System.currentTimeMillis() - startTime);
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
            pool.submit(() -> {
                if (reason.matches(log)
//                        || true //dubug too see all available warnings
                ) {
                    synchronized (pool) {
                        KnownCrashReasonMessage.addCrashReasonMessage(
                                new KnownCrashReasonMessage(log, reason)
                        );
                    }
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
        registerKnownCrashReason(new nglMultiDrawElementsBaseVertex());
        registerKnownCrashReason(new Nvoglv64());

        registerKnownCrashReason(new Create6Addons());
        registerKnownCrashReason(new CtovWithoutLithostitched());
        registerKnownCrashReason(new CurseForgeCorrupted());
        registerKnownCrashReason(new DiskSpaceEnded());
        registerKnownCrashReason(new DuplicatedMods());
        registerKnownCrashReason(new EpicFightAddons());
        registerKnownCrashReason(new FeatureOrderCycle());
        registerKnownCrashReason(new GroovyModLoaderIPv6());
        registerKnownCrashReason(new KubeJSDataPack());
        registerKnownCrashReason(new LanguageProviderMismatch());
        registerKnownCrashReason(new LegacyTooManyIds());
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
        registerKnownCrashReason(new UnsupportedClassVersion());
        registerKnownCrashReason(new UsedByAnotherProcess());
        registerKnownCrashReason(new Version1_21());

        registerKnownCrashReason(new WasClosedByWindows());


        registerCodexKnownCrashReason(new ErroringEntity());

        reasonsRegistered = true;
    }
}
