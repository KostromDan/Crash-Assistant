package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.win_event;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.RegexChecker;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public class WasClosedByWindows extends KnownCrashReason {
    public static final List<String> wasClosedMessagesRegex = Stream.of(
                    "The program java\\w*(?:\\.\\w+)? version \\S+ stopped interacting with Windowsand was closed\\. To see if more information about the problem is available,check the problem history in the Security and Maintenance control panel\\.",
                    "Программа java\\w*(?:\\.\\w+)? версии \\S+ перестала взаимодействовать с Windows и была закрыта\\. Дополнительные сведения о проблеме см\\. в журнале проблем на панели управления \"Безопасность и обслуживание\"\\.",
                    "Le programme java\\w*(?:\\.\\w+)? version \\S+ a cessé d'interagir avec Windows eta été fermé\\. Pour savoir si vous disposez de plus d'informations sur leproblème, consultez l'historique des problèmes dans le panneau deconfiguration Sécurité et maintenance\\.",
                    "Das Programm java\\w*(?:\\.\\w+)? Version \\S+ hat die Interaktion mit Windowsbeendet und wurde geschlossen\\. Überprüfen Sie den Problemverlauf in derSystemsteuerung \"Sicherheit und Wartung\", um nach weiteren Informationen zumProblem zu suchen\\.",
                    "El programa java\\w*(?:\\.\\w+)? versión \\S+ dejó de interactuar con Windowsy se cerró\\. Para ver si hay más información disponible sobre esteproblema, comprueba el historial de problemas en el panel de control deSeguridad y mantenimiento\\.",
                    "Programmet java\\w*(?:\\.\\w+)?, version \\S+ avslutades eftersom det slutade samverka med Windows\\. Ytterligare information om problemet kan finnas i problemhistoriken i Säkerhet och underhåll på Kontrollpanelen\\.",
                    "Programma java\\w*(?:\\.\\w+)? versie \\S+ communiceert niet meer met Windows en is gesloten\\. Als u wilt zien of er meer informatie over het probleem beschikbaar is, controleert u de probleemgeschiedenis in het configuratiescherm van Beveiliging en onderhoud\\.",
                    "프로그램 java\\w*(?:\\.\\w+)? 버전 \\S+이\\(가\\) Windows와의 상호 작용을 중지하고 닫혔습니다\\. 문제에 대한 추가 정보가 있는지 확인하려면 보안 및 유지 관리 제어판에서 문제 기록을 확인하세요\\.")
            .map(WasClosedByWindows::removeSpacesAndEndLines).toList();

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
    public boolean matches(String logText, Log log) {
        try {
            log.getProcessor().processLogFile();
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Error while reading " + log.getFileName() + " file: ", e);
        }
        return RegexChecker.logContainsOneOfPatterns(removeSpacesAndEndLines(log.getProcessor().getAllLinesString()), log.getPath(), patterns);
    }
}
