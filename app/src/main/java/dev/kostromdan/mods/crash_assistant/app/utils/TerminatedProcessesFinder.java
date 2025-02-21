package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TerminatedProcessesFinder {
    public static final List<String> wasClosedMessagesRegex = new ArrayList<>() {{
        add("The program java\\w*(?:\\.\\w+)? version \\S+ stopped interacting with Windowsand was closed\\. To see if more information about the problem is available,check the problem history in the Security and Maintenance control panel\\.");
        add("Программа java\\w*(?:\\.\\w+)? версии \\S+ перестала взаимодействовать с Windows и была закрыта\\. Дополнительные сведения о проблеме см\\. в журнале проблем на панели управления \"Безопасность и обслуживание\"\\.");
        add("Le programme java\\w*(?:\\.\\w+)? version \\S+ a cessé d'interagir avec Windows eta été fermé\\. Pour savoir si vous disposez de plus d'informations sur leproblème, consultez l'historique des problèmes dans le panneau deconfiguration Sécurité et maintenance\\.");
        add("Das Programm java\\w*(?:\\.\\w+)? Version \\S+ hat die Interaktion mit Windowsbeendet und wurde geschlossen\\. Überprüfen Sie den Problemverlauf in derSystemsteuerung \"Sicherheit und Wartung\", um nach weiteren Informationen zumProblem zu suchen\\.");
        add("El programa java\\w*(?:\\.\\w+)? versión \\S+ dejó de interactuar con Windowsy se cerró\\. Para ver si hay más información disponible sobre esteproblema, comprueba el historial de problemas en el panel de control deSeguridad y mantenimiento\\.");
        add("Programmet java\\w*(?:\\.\\w+)?, version \\S+ avslutades eftersom det slutade samverka med Windows\\. Ytterligare information om problemet kan finnas i problemhistoriken i Säkerhet och underhåll på Kontrollpanelen\\.");
        add("Programma java\\w*(?:\\.\\w+)? versie \\S+ communiceert niet meer met Windows en is gesloten\\. Als u wilt zien of er meer informatie over het probleem beschikbaar is, controleert u de probleemgeschiedenis in het configuratiescherm van Beveiliging en onderhoud\\.");
        add("프로그램 java\\w*(?:\\.\\w+)? 버전 \\S+이\\(가\\) Windows와의 상호 작용을 중지하고 닫혔습니다\\. 문제에 대한 추가 정보가 있는지 확인하려면 보안 및 유지 관리 제어판에서 문제 기록을 확인하세요\\.");
    }};

    static {
        for (int i = 0; i < wasClosedMessagesRegex.size(); i++) {
            wasClosedMessagesRegex.set(i, wasClosedMessagesRegex.get(i).replaceAll(" ", ""));
        }
    }

    public static String removeSpacesAndEndLines(String s){
        return s.replaceAll("[ ]|\\s*\\n\\s*", "");
    }

    public static String getTerminatedByWinProcessLogs() {
        synchronized (TerminatedProcessesFinder.class) {
            String fileName = "win_event" + System.currentTimeMillis() + ".txt";
            Path path = Paths.get(fileName);
            String command = "$ErrorActionPreference = 'Continue'; \n" +
                    "Get-WinEvent -FilterHashtable @{ \n" +
                    "  LogName='Application'; \n" +
                    "  Level=2; \n" +
                    "  StartTime=(Get-Date).AddMinutes(-1) \n" +
                    "} *>&1 | Format-Table -Wrap -AutoSize | Out-File \"$FILE_NAME$\" -Encoding UTF8".replace("$FILE_NAME$", fileName);
            try {
                Process process = new ProcessBuilder("powershell.exe", "-Command", command.replaceAll("\\n", ""))
                        .redirectErrorStream(true)
                        .start();

                process.waitFor();
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error wile executing PowerShell command for finding terminated processes.", e);
            }

            try {
                String fileContents = Files.readString(path);
                if (fileContents.contains("NoMatchingEventsFound") || fileContents.length() <= 8) {
                    Files.deleteIfExists(path);
                    return fileName;
                }
                String output = "Detected that Windows has terminated one or more processes within the last minute leading up to the moment of the current Minecraft JVM instance termination,\n" +
                        "so with a very high probability that one of these processes is the Minecraft JVM itself.\n" +
                        "Command used to identify terminated processes:\n \n"
                        + command
                        + "\n \nIf no java.exe (or related JVM processes) are listed below, you can disregard this message.\n" +
                        "To get more information about such errors:\n" +
                        "1) Open Windows Event Viewer (Win+R -> eventvwr.msc -> Enter).\n" +
                        "2) Click \"Windows Logs\" -> \"Application\".\n" +
                        "3) Look for the latest Error you have.\n \n "
                        + fileContents;

                Files.writeString(path, output);
                KnownCrashReason.addIfContainsOneOfPatterns(removeSpacesAndEndLines(fileContents), path, LanguageProvider.get("warnings.closed_by_windows"), wasClosedMessagesRegex);
            } catch (Exception ignored) {
            }

            return fileName;
        }
    }
}
