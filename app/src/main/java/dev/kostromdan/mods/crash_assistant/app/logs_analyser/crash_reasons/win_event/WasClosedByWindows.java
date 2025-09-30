package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.win_event;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.RegexChecker;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WasClosedByWindows extends KnownCrashReason {
    public static final List<String> wasClosedMessagesRegex = Stream.of(
            "The program java\\w*(?:\\.\\w+)? version \\S+ stopped interacting with Windowsand was closed\\.",
            "Программа java\\w*(?:\\.\\w+)? версии \\S+ перестала взаимодействовать с Windows и была закрыта\\.",
            "Le programme java\\w*(?:\\.\\w+)? version \\S+ a cessé d'interagir avec Windows eta été fermé\\.",
            "Das Programm java\\w*(?:\\.\\w+)? Version \\S+ hat die Interaktion mit Windows beendet und wurde geschlossen\\.",
            "Das Programm java\\w*(?:\\.\\w+)? Version \\S+ hat aufgehört mit Windows zu interagieren und wurde geschlossen\\.",
            "El programa java\\w*(?:\\.\\w+)? versión \\S+ dejó de interactuar con Windowsy se cerró\\.",
            "Programmet java\\w*(?:\\.\\w+)?, version \\S+ avslutades eftersom det slutade samverka med Windows\\.",
            "Programmet java\\w*(?:\\.\\w+)? version \\S+ slutade interagera med Windows och stängdes\\.",
            "Programma java\\w*(?:\\.\\w+)? versie \\S+ communiceert niet meer met Windows en is gesloten\\.",
            "프로그램 java\\w*(?:\\.\\w+)? 버전 \\S+이\\(가\\) Windows와의 상호 작용을 중지하고 닫혔습니다\\.",
            "Verze \\S+ programu java\\w*(?:\\.\\w+)? ukončila interakci se systémem Windows a byla ukončena\\.",
            "O programa java\\w*(?:\\.\\w+)? versão \\S+ interagiu com o Windows e foi fechado\\.",
            "O programa java\\w*(?:\\.\\w+)? versão \\S+ parou de interagir com o Windows e foi fechado\\.",
            "Il programma java\\w*(?:\\.\\w+)? versione \\S+ interrotto l'interazione con Windows ed è stato chiuso\\.",
            "Program java\\w*(?:\\.\\w+)? w wersji \\S+ przestał współpracować z systemem Windows i został zamknięty\\.",
            "Program java\\w*(?:\\.\\w+)? w wersji \\S+ przestał korzystać z systemu Windows i został zamknięty\\."
    ).map(WasClosedByWindows::removeSpacesAndEndLines).collect(Collectors.toList());

    public static String removeSpacesAndEndLines(String s) {
        return s.replaceAll("[ ]|\\s*\\n\\s*", "");
    }

    public WasClosedByWindows() {
        super(
                LogType.WIN_EVENT,
                LanguageProvider.get("warnings.closed_by_windows"),
                wasClosedMessagesRegex
        );
    }

    @Override
    public boolean matches(Log log) {
        log.getReader().readLogFileSafe();
        return RegexChecker.logContainsOneOfPatterns(removeSpacesAndEndLines(log.getReader().getAllLinesString()), log.getPath(), patterns);
    }
}
