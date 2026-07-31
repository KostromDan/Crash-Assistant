package dev.kostromdan.mods.crash_assistant.app.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.FilePanel;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis.UploadLogResponse;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.info.CrashAssistantInfo;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class MclogArrayRegistrar {
    private static final String DEFAULT_API_URL = "https://api.kostromdan.dev/v1/crash-assistant/mclog-arrays";
    private static final String DEFAULT_ITEM_DELETE_API_URL =
            "https://api.kostromdan.dev/v1/crash-assistant/mclog-arrays/items/delete";
    private static final Pattern ARRAY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,64}$");
    public static final int LAST_PRIORITY = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ARRAY_TOKEN = generateArrayToken();
    private static final Set<String> REGISTERED_KEYS = ConcurrentHashMap.newKeySet();
    private static final Set<CompletableFuture<Void>> PENDING_REGISTRATIONS = ConcurrentHashMap.newKeySet();
    private static final CompletableFuture<String> FIRST_SUCCESSFUL_ARRAY_REGISTRATION = new CompletableFuture<>();
    private static final Object CRASH_ASSISTANT_REPLACEMENT_LOCK = new Object();
    private static CompletableFuture<Void> previousCrashAssistantReplacement = CompletableFuture.completedFuture(null);
    private static List<RegistrationItem> registeredCrashAssistantItems = Collections.emptyList();

    private MclogArrayRegistrar() {
    }

    public static void registerUploadedLog(Log log, String logId, long created, String fileName, int priority) {
        registerUploadedLog(logId, created, fileName, log.getType().name(), priority);
    }

    public static void registerUploadedLog(String logId, long created, String fileName, String logType, int priority) {
        if (!CrashAssistantConfig.getBoolean("general.send_uploaded_logs_data_to_kostromdan_dev")) {
            return;
        }
        if (logId == null || logId.trim().isEmpty() || created <= 0) {
            return;
        }
        String key = logId + "\n" + created + "\n" + fileName + "\n" + priority;
        if (!REGISTERED_KEYS.add(key)) {
            return;
        }

        CompletableFuture<Void> pendingRegistration = new CompletableFuture<>();
        PENDING_REGISTRATIONS.add(pendingRegistration);
        Thread thread = new Thread(() -> {
            try {
                JsonObject body = buildRequest(Collections.singletonList(
                        new RegistrationItem(logId, created, fileName, logType, priority)));
                RegistrationResponse response = postJson(getApiUrl(), body.toString());
                if (response.status < 200 || response.status >= 300) {
                    CrashAssistantApp.LOGGER.warn("Mclog array registration returned HTTP {} for {} ({})", response.status, fileName, logId);
                } else if (response.arrayId == null) {
                    CrashAssistantApp.LOGGER.warn("Mclog array registration returned no valid array ID for {} ({})", fileName, logId);
                } else {
                    FIRST_SUCCESSFUL_ARRAY_REGISTRATION.complete(response.arrayId);
                }
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.warn("Failed to register uploaded log in mclog array: {} ({})", fileName, logId, e);
            } finally {
                pendingRegistration.complete(null);
                PENDING_REGISTRATIONS.remove(pendingRegistration);
            }
        }, "MclogArrayRegistrar");
        thread.setDaemon(true);
        thread.start();
    }

    public static void replaceCrashAssistantUploadedLog(
            Log log,
            UploadLogResponse firstLines,
            UploadLogResponse lastLines
    ) {
        List<RegistrationItem> replacement = new ArrayList<>();
        String firstFileName = lastLines == null ? log.getFileName() : log.getFileName() + " head";
        replacement.add(new RegistrationItem(
                firstLines.getId(),
                firstLines.getCreated(),
                firstFileName,
                log.getType().name(),
                priorityFor(log, 0)));
        if (lastLines != null) {
            replacement.add(new RegistrationItem(
                    lastLines.getId(),
                    lastLines.getCreated(),
                    log.getFileName() + " tail",
                    log.getType().name(),
                    priorityFor(log, 1)));
        }
        if (replacement.stream().anyMatch(item -> !item.isValid())) {
            CrashAssistantApp.LOGGER.warn("Cannot update Crash Assistant log metadata because the upload response is invalid.");
            return;
        }

        boolean registerReplacement = CrashAssistantConfig.getBoolean(
                "general.send_uploaded_logs_data_to_kostromdan_dev");
        CompletableFuture<Void> pendingReplacement = new CompletableFuture<>();
        CompletableFuture<Void> predecessor;
        synchronized (CRASH_ASSISTANT_REPLACEMENT_LOCK) {
            predecessor = previousCrashAssistantReplacement;
            previousCrashAssistantReplacement = pendingReplacement;
            PENDING_REGISTRATIONS.add(pendingReplacement);
        }

        Thread thread = new Thread(() -> {
            try {
                predecessor.join();

                List<RegistrationItem> itemsToDelete;
                synchronized (CRASH_ASSISTANT_REPLACEMENT_LOCK) {
                    itemsToDelete = new ArrayList<>(registeredCrashAssistantItems);
                }

                boolean registrationSucceeded = !registerReplacement;
                if (registerReplacement) {
                    try {
                        RegistrationResponse response = postJson(
                                getApiUrl(),
                                buildRequest(replacement).toString());
                        if (response.status < 200 || response.status >= 300) {
                            CrashAssistantApp.LOGGER.warn(
                                    "Crash Assistant log metadata registration returned HTTP {}.",
                                    response.status);
                        } else if (response.arrayId == null) {
                            CrashAssistantApp.LOGGER.warn(
                                    "Crash Assistant log metadata registration returned no valid array ID.");
                        } else {
                            FIRST_SUCCESSFUL_ARRAY_REGISTRATION.complete(response.arrayId);
                            registrationSucceeded = true;
                        }
                    } catch (Exception e) {
                        CrashAssistantApp.LOGGER.warn("Failed to register replacement Crash Assistant log metadata.", e);
                    }
                }

                List<RegistrationItem> itemsStillRegistered = new ArrayList<>();
                if (registrationSucceeded) {
                    for (RegistrationItem item : itemsToDelete) {
                        if (!deleteRegisteredItem(item)) {
                            itemsStillRegistered.add(item);
                        }
                    }
                } else {
                    itemsStillRegistered.addAll(itemsToDelete);
                }
                if (registerReplacement) {
                    // Keep the exact identities even after an ambiguous network failure. The next replacement can
                    // safely delete them because the item-delete endpoint is idempotent for an already absent item.
                    itemsStillRegistered.addAll(replacement);
                }

                synchronized (CRASH_ASSISTANT_REPLACEMENT_LOCK) {
                    registeredCrashAssistantItems = Collections.unmodifiableList(itemsStillRegistered);
                }
            } finally {
                pendingReplacement.complete(null);
                PENDING_REGISTRATIONS.remove(pendingReplacement);
            }
        }, "MclogArrayRegistrar-CrashAssistant");
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

    public static String getArrayTokenForStorage() {
        return CrashAssistantConfig.getBoolean("general.send_uploaded_logs_data_to_kostromdan_dev")
                ? ARRAY_TOKEN
                : null;
    }

    public static CompletableFuture<Void> waitForPendingRegistrations() {
        CompletableFuture<?>[] pending = PENDING_REGISTRATIONS.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(pending);
    }

    public static void onFirstSuccessfulArrayRegistration(Consumer<String> consumer) {
        FIRST_SUCCESSFUL_ARRAY_REGISTRATION.thenAccept(consumer);
    }

    private static JsonObject buildRequest(List<RegistrationItem> items) {
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
        for (RegistrationItem registrationItem : items) {
            JsonObject item = new JsonObject();
            item.addProperty("logId", registrationItem.logId);
            item.addProperty("created", registrationItem.created);
            item.addProperty("fileName", registrationItem.fileName);
            addIfPresent(item, "logType", registrationItem.logType);
            item.addProperty("priority", registrationItem.priority);
            logs.add(item);
        }
        body.add("logs", logs);
        return body;
    }

    private static boolean deleteRegisteredItem(RegistrationItem item) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(getItemDeleteApiUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "CrashAssistant");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);

            JsonObject request = new JsonObject();
            request.addProperty("arrayToken", ARRAY_TOKEN);
            request.addProperty("logId", item.logId);
            request.addProperty("created", item.created);
            byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, responseCode);
            if (responseCode < 200 || responseCode >= 300) {
                CrashAssistantApp.LOGGER.warn(
                        "Crash Assistant log metadata deletion returned HTTP {} for {} ({}).",
                        responseCode,
                        item.fileName,
                        item.logId);
                return false;
            }
            JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!response.has("status") || !"ok".equals(response.get("status").getAsString())) {
                CrashAssistantApp.LOGGER.warn(
                        "Crash Assistant log metadata deletion was rejected for {} ({}).",
                        item.fileName,
                        item.logId);
                return false;
            }
            CrashAssistantApp.LOGGER.info(
                    "Removed previous Crash Assistant log metadata for {} ({}).",
                    item.fileName,
                    item.logId);
            UploadedLogsManager.markMetadataDeletedByLogIds(Collections.singleton(item.logId));
            return true;
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn(
                    "Failed to remove previous Crash Assistant log metadata for "
                            + item.fileName + " (" + item.logId + ").",
                    e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readResponseBody(HttpURLConnection connection, int responseCode) throws Exception {
        InputStream responseStream = responseCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (responseStream == null) {
            return "";
        }
        StringBuilder responseBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseStream,
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
        }
        return responseBody.toString();
    }

    private static RegistrationResponse postJson(String apiUrl, String json) throws Exception {
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
        StringBuilder responseBody = new StringBuilder();
        InputStream responseStream = responseCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (responseStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseStream,
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
            }
        }

        String arrayId = null;
        if (responseCode >= 200 && responseCode < 300 && responseBody.length() > 0) {
            JsonObject response = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
            if (response.has("status")
                    && "ok".equals(response.get("status").getAsString())
                    && response.has("arrayId")
                    && !response.get("arrayId").isJsonNull()) {
                String candidate = response.get("arrayId").getAsString();
                if (ARRAY_ID_PATTERN.matcher(candidate).matches()) {
                    arrayId = candidate;
                }
            }
        }
        return new RegistrationResponse(responseCode, arrayId);
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

    private static String getItemDeleteApiUrl() {
        return System.getProperty(
                "crash_assistant.mclog_array_item_delete_api_url",
                DEFAULT_ITEM_DELETE_API_URL);
    }

    private static String generateArrayToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class RegistrationResponse {
        private final int status;
        private final String arrayId;

        private RegistrationResponse(int status, String arrayId) {
            this.status = status;
            this.arrayId = arrayId;
        }
    }

    private static final class RegistrationItem {
        private final String logId;
        private final long created;
        private final String fileName;
        private final String logType;
        private final int priority;

        private RegistrationItem(String logId, long created, String fileName, String logType, int priority) {
            this.logId = logId;
            this.created = created;
            this.fileName = fileName;
            this.logType = logType;
            this.priority = priority;
        }

        private boolean isValid() {
            return logId != null && !logId.trim().isEmpty() && created > 0;
        }
    }
}
