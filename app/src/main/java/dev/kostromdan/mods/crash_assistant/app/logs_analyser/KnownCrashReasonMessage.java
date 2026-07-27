package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.CodexMessage;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.Problem;

import java.util.*;
import java.util.stream.Collectors;

public class KnownCrashReasonMessage {
    private static final Comparator<KnownCrashReasonMessage> CRASH_REASON_COMPARATOR = Comparator
            .comparingInt((KnownCrashReasonMessage msg) -> msg.getReason().getPriority())
            .reversed()
            .thenComparing(msg -> msg.getReason().getReasonName().toLowerCase())
            .thenComparing(System::identityHashCode);
    private static final SortedSet<KnownCrashReasonMessage> crashReasonMessages =
            Collections.synchronizedSortedSet(
                    new TreeSet<>(CRASH_REASON_COMPARATOR)
            );
    private boolean shownWarn;
    private final Log log;
    private final KnownCrashReason reason;
    private boolean isCodexMessage = false;

    public Log getLog() {
        return log;
    }

    public KnownCrashReasonMessage(Log log, KnownCrashReason reason) {
        this.shownWarn = false;
        this.log = log;
        this.reason = reason;
    }

    public boolean isShownWarn() {
        return shownWarn;
    }

    public void setShownWarn(boolean value) {
        shownWarn = value;
    }

    public String getMessage() {
        if (log == null) {
            return reason.getMessage();
        }
        return reason.getMessage().replaceAll("\\$LOG_FILENAME\\$", log.getFileName());
    }

    public KnownCrashReason getReason() {
        return reason;
    }

    public static Set<KnownCrashReasonMessage> getAllMessages() {
        return crashReasonMessages;
    }

    public static List<KnownCrashReasonMessage> getAllMessagesSnapshot() {
        synchronized (crashReasonMessages) {
            return new ArrayList<>(crashReasonMessages);
        }
    }

    public static HashMap<KnownCrashReason, List<Log>> getUniqueMessages() {
        HashMap<String, List<KnownCrashReasonMessage>> messagesByReasonType = new HashMap<>();
        HashMap<KnownCrashReason, List<Log>> result = new HashMap<>();

        // Group messages by reason name
        for (KnownCrashReasonMessage message : getAllMessagesSnapshot()) {
            String reasonType = message.getReason().getReasonName().toLowerCase();
            messagesByReasonType.computeIfAbsent(reasonType, k -> new ArrayList<>()).add(message);
        }

        // For each unique reason type, select the first message and collect all associated logs
        for (List<KnownCrashReasonMessage> messages : messagesByReasonType.values()) {
            if (!messages.isEmpty()) {
                KnownCrashReasonMessage firstMessage = messages.get(0);
                KnownCrashReason reason = firstMessage.getReason();

                // Collect all logs for this reason type
                List<Log> logs = messages.stream()
                        .map(KnownCrashReasonMessage::getLog)
                        .collect(Collectors.toList());

                result.put(reason, logs);
            }
        }

        return result;
    }

    public static void addCrashReasonMessage(KnownCrashReasonMessage msg) {
        crashReasonMessages.add(msg);
    }

    public static void addCodexMessage(Log log, Problem problem, String url) {
        int line = problem.getLine();
        String solutions = String.join("\n", problem.getSolutions());
        String crashAssistantAnalysisOfCodex = LogAnalyser.analyseCodexMessage(problem.getMessage() + "\n" + solutions);
        String msg = LanguageProvider.get("warnings.codex")
                .replaceAll("\\$PROBLEM\\$", problem.getMessage())
                .replaceAll("\\$LINE\\$", "<a href='" + url + "#L" + line + "'>" + LanguageProvider.get("warnings_common.line") + " " + line + "</a>")
                .replaceAll("\\$SOLUTIONS\\$", solutions);
        if (!crashAssistantAnalysisOfCodex.isEmpty()) {
            msg += "\n\n" + LanguageProvider.get("warnings.codex_crash_assistant_comment") + "\n" + crashAssistantAnalysisOfCodex;
        }
        KnownCrashReasonMessage codexMsg = new KnownCrashReasonMessage(log, new CodexMessage(msg));
        codexMsg.isCodexMessage = true;
        addCrashReasonMessage(codexMsg);
    }

    public boolean isCodexMessage() {
        return isCodexMessage;
    }
}
