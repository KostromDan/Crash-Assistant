package dev.kostromdan.mods.crash_assistant.app.scripts;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.introspection.JexlPermissions;

public class Permissions {
    private static JexlEngine engine = null;

    public static JexlEngine getEngine() {
        if (engine != null) {
            return engine;
        }
        engine = new JexlBuilder()
                .permissions(
                        JexlPermissions.parse(
                                "# Allow:",

                                "java.lang.*",
                                "java.math.*",
                                "java.text.*",
                                "java.time.*",
                                "java.util.*",
                                "org.apache.commons.jexl3.*",
                                "dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed.*",
                                "dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.*",
                                "dev.kostromdan.mods.crash_assistant.app.logs_analyser.*",
                                "dev.kostromdan.mods.crash_assistant.app.logs_analyser { +Log {} +LogComparator {} +LogReader {} +LogType {} +LogsList {} +RegexChecker {} }",

                                "# Deny:",

                                "java.lang { ApplicationShutdownHooks {} Class {} ClassLoader {} Compiler {} IO {}" +
                                        " InheritableThreadLocal {} LiveStackFrame {} LiveStackFrameInfo {} Module {}" +
                                        " ModuleLayer {} Package {} Process {} ProcessBuilder {}" +
                                        " ProcessEnvironment {} ProcessHandle {} ProcessHandleImpl {} ProcessImpl {}" +
                                        " Runtime {} RuntimePermission {} ScopedValue {} SecurityManager {}" +
                                        " ServiceLoader {} Shutdown {} StackFrameInfo {} StackTraceElement {}" +
                                        " StackWalker {} System {} Terminator {} Thread {} ThreadBuilders {}" +
                                        " ThreadGroup {} ThreadLocal {} VirtualThread {} }",
                                "java.lang.annotation {}",
                                "java.lang.classfile {}",
                                "java.lang.classfile.attribute {}",
                                "java.lang.classfile.constantpool {}",
                                "java.lang.classfile.instruction {}",
                                "java.lang.constant {}",
                                "java.lang.foreign {}",
                                "java.lang.instrument {}",
                                "java.lang.invoke {}",
                                "java.lang.management {}",
                                "java.lang.module {}",
                                "java.lang.ref {}",
                                "java.lang.reflect {}",
                                "java.lang.runtime {}",
                                "java.util { EventListener {} EventObject {} Properties {} PropertyPermission {} " +
                                        "PropertyResourceBundle {} ResourceBundle {} ServiceLoader {} Timer {}" +
                                        " TimerTask {} }",
                                "java.util.concurrent { AbstractExecutorService {} BrokenBarrierException {}" +
                                        " Callable {} CancellationException {} CompletableFuture {}" +
                                        " CompletionException {} CompletionService {} CompletionStage {}" +
                                        " CountDownLatch {} CountedCompleter {} CyclicBarrier {} DelayScheduler {}" +
                                        " Exchanger {} ExecutionException {} Executor {} ExecutorCompletionService {}" +
                                        " Executors {} ExecutorService {} Flow {} ForkJoinPool {} ForkJoinTask {}" +
                                        " ForkJoinWorkerThread {} Future {} FutureTask {} Helpers {} Joiners {}" +
                                        " Phaser {} RecursiveAction {} RecursiveTask {}" +
                                        " RejectedExecutionException {} RejectedExecutionHandler {} RunnableFuture {}" +
                                        " RunnableScheduledFuture {} ScheduledExecutorService {} ScheduledFuture {}" +
                                        " ScheduledThreadPoolExecutor {} Semaphore {} StructuredTaskScope {}" +
                                        " StructuredTaskScopeImpl {} StructureViolationException {}" +
                                        " SubmissionPublisher {} SynchronousQueue {} ThreadFactory {}" +
                                        " ThreadPerTaskExecutor {} ThreadPoolExecutor {} TimeoutException {} }",
                                "java.util.concurrent.atomic { AtomicIntegerFieldUpdater {} AtomicLongFieldUpdater {}" +
                                        " AtomicReferenceFieldUpdater {} }",
                                "java.util.concurrent.locks {}",
                                "java.util.jar {}",
                                "java.util.logging {}",
                                "java.util.prefs {}",
                                "java.util.spi {}",
                                "java.util.zip {}",

                                "org.apache.commons.jexl3 { JexlBuilder {} JexlEngine {} JexlOptions {} JxltEngine {} }",
                                "org.apache.commons.jexl3.annotations {}",
                                "org.apache.commons.jexl3.internal {}",
                                "org.apache.commons.jexl3.internal.introspection {}",
                                "org.apache.commons.jexl3.introspection {}",
                                "org.apache.commons.jexl3.parser {}",
                                "org.apache.commons.jexl3.scripting {}"
                        )
                ).strict(true)
                .create();
        return engine;
    }


}
