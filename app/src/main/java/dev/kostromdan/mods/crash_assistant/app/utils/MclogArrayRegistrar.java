package dev.kostromdan.mods.crash_assistant.app.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.FilePanel;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.info.CrashAssistantInfo;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MclogArrayRegistrar {
    private static final String DEFAULT_API_URL = "https://api.kostromdan.dev/v1/crash-assistant/mclog-arrays";
    public static final int LAST_PRIORITY = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ARRAY_TOKEN = generateArrayToken();
    private static final Set<String> REGISTERED_KEYS = ConcurrentHashMap.newKeySet();

    private MclogArrayRegistrar() {
    }

    public static void registerUploadedLog(Log log, String logId, String fileName, int priority) {
        registerUploadedLog(logId, fileName, log.getType().name(), priority);
    }

    public static void registerUploadedLog(String logId, String fileName, String logType, int priority) {
        if (!CrashAssistantConfig.getBoolean("general.send_uploaded_logs_data_to_kostromdan_dev")) {
            return;
        }
        if (logId == null || logId.trim().isEmpty()) {
            return;
        }
        String key = logId + "\n" + fileName + "\n" + priority;
        if (!REGISTERED_KEYS.add(key)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                JsonObject body = buildRequest(logId, fileName, logType, priority);
                int status = postJson(getApiUrl(), body.toString());
                if (status < 200 || status >= 300) {
                    CrashAssistantApp.LOGGER.warn("Mclog array registration returned HTTP {} for {} ({})", status, fileName, logId);
                }
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.warn("Failed to register uploaded log in mclog array: {} ({})", fileName, logId, e);
            }
        }, "MclogArrayRegistrar");
        thread.setDaemon(true);
        thread.start();
    }

    public static int priorityFor(Log log, int partOffset) {
        if (CrashAssistantGUI.fileListPanel == null) {
            return log.getType().ordinal() * 10 + partOffset;
        }

        int index = 0;
        for (FilePanel panel : CrashAssistantGUI.fileListPanel.getFilePanelList()) {
            if (panel.getLog() == log) {
                return (index + 1) * 10 + partOffset;
            }
            index++;
        }
        return log.getType().ordinal() * 10 + partOffset;
    }

    private static JsonObject buildRequest(String logId, String fileName, String logType, int priority) {
        JsonObject body = new JsonObject();
        body.addProperty("arrayToken", ARRAY_TOKEN);
        addIfPresent(body, "minecraftVersion", PlatformHelp.minecraftVersion);
        addIfPresent(body, "loader", PlatformHelp.platform.name());
        addIfPresent(body, "loaderJarName", PlatformHelp.loaderJarName);
        addIfPresent(body, "crashAssistantJarName", Boot.crashAssistantModJarName);
        addIfPresent(body, "crashAssistantVersion", CrashAssistantInfo.getVersion());
        addIfPresent(body, "javaVersion", PlatformHelp.javaVersion);
        addIfPresent(body, "os", PlatformHelp.OS);
        addIfPresent(body, "minecraftUuid", UUIDUtils.getUUID());

        JsonArray logs = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("logId", logId);
        item.addProperty("fileName", fileName);
        addIfPresent(item, "logType", logType);
        item.addProperty("priority", priority);
        logs.add(item);
        body.add("logs", logs);
        return body;
    }

    private static int postJson(String apiUrl, String json) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", "CrashAssistant");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
        }

        int responseCode = connection.getResponseCode();
        try (BufferedReader ignored = new BufferedReader(new InputStreamReader(
                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8))) {
            while (ignored.readLine() != null) {
                // Drain the response so the connection can be reused.
            }
        } catch (Exception ignored) {
        }
        return responseCode;
    }

    private static void addIfPresent(JsonObject body, String key, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "UNDEFINED".equalsIgnoreCase(trimmed)) {
            return;
        }
        body.addProperty(key, trimmed);
    }

    private static String getApiUrl() {
        return System.getProperty("crash_assistant.mclog_array_api_url", DEFAULT_API_URL);
    }

    private static String generateArrayToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
