package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.CodexMessage;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import gs.mclo.api.response.insights.Problem;
import gs.mclo.api.response.insights.Solution;

import java.util.*;

public class KnownCrashReasonMessage {
    private static final List<KnownCrashReasonMessage> crashReasonMessages = Collections.synchronizedList(new ArrayList<>());
    private boolean shownWarn;
    private final Log log;
    private final KnownCrashReason reason;
    private boolean isCodexMessage = false;

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
        return reason.getMessage().replaceAll("\\$LOG_FILENAME\\$", log.getFileName());
    }

    public KnownCrashReason getReason() {
        return reason;
    }

    public static List<KnownCrashReasonMessage> getAllMessages() {
        return crashReasonMessages;
    }

    public static void addCrashReasonMessage(KnownCrashReasonMessage msg) {
        crashReasonMessages.add(msg);
    }

    public static void addCodexMessage(Log log, Problem problem, String url) {
        int line = problem.getEntry().getLines()[0].getNumber();
        String solutions = String.join("\n", Arrays.stream(problem.getSolutions()).map(Solution::getMessage).toList());
        String crashAssistantAnalysisOfCodex = LogAnalyser.analyseCodexMessage(problem.getMessage() + "\n" + solutions);
        String msg = LanguageProvider.get("warnings.codex")
                .replaceAll("\\$PROBLEM\\$", problem.getMessage())
                .replaceAll("\\$LINE\\$", "<a href='" + url + "#L" + line + "'>" + LanguageProvider.get("warnings_common.line") + " " + line + "</a>")
                .replaceAll("\\$SOLUTIONS\\$", solutions);
        if (!crashAssistantAnalysisOfCodex.isEmpty()) {
            msg += "\n\n" + LanguageProvider.get("warnings.codex_crash_assistant_comment") + "\n" + crashAssistantAnalysisOfCodex;
        }
        KnownCrashReasonMessage codexMsg = new KnownCrashReasonMessage(log, new CodexMessage(msg));
        addCrashReasonMessage(codexMsg);
        codexMsg.isCodexMessage = true;
    }

    public boolean isCodexMessage() {
        return isCodexMessage;
    }
}
