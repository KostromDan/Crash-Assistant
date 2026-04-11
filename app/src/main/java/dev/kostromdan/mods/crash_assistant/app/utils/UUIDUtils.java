package dev.kostromdan.mods.crash_assistant.app.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.gui.ControlPanel;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.kostromdan.mods.crash_assistant.app.utils.UUIDCheckStatus.*;

public class UUIDUtils {
    public static volatile UUIDCheckStatus status = UNDEFINED;
    private static volatile long checkStartTime = 0;
    private static final AtomicBoolean isStarted = new AtomicBoolean(false);

    public static String getParam(String paramName) {
        if (Boot.MINECRAFT_LAUNCH_COMMAND == null || Boot.MINECRAFT_LAUNCH_COMMAND.isEmpty()) {
            return null;
        }

        Pattern ARG_PATTERN = Pattern.compile("--" + paramName + "[\\s=:,]+([^\\s,]+)");
        Matcher matcher = ARG_PATTERN.matcher(Boot.MINECRAFT_LAUNCH_COMMAND);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String getUUID() {
        return getParam("uuid");
    }

    public static String getUsername() {
        return getParam("username");
    }

    public static void startCheck() {
        if (isStarted.getAndSet(true)) {
            return;
        }

        checkStartTime = System.currentTimeMillis();
        status = PROCESSING;

        new Thread(() -> {
            String uuid = getUUID();

            if (uuid == null) {
                status = FAILED;
                return;
            }

            for (int i = 0; i < 3; i++) {
                if (System.currentTimeMillis() - checkStartTime > 6000) {
                    break;
                }

                UUIDCheckStatus currentResult = verifyUUID(uuid);

                if (currentResult == LICENSED || currentResult == PIRACY_OR_OFFLINE) {
                    status = currentResult;
                    CrashAssistantApp.LOGGER.info("UUID({}) verification result: {}", uuid, status);
                    if (currentResult == PIRACY_OR_OFFLINE && (CrashAssistantConfig.getBoolean("piracy.enabled") || PlatformHelp.isLinkDefault())) {
                        if (PlatformHelp.isLinkDefault() && PlatformHelp.platform == PlatformHelp.CLEANROOM) return;
                        SwingUtilities.invokeLater(() -> {
                            ControlPanel.requestHelpButton.setVisible(false);
                            CrashAssistantGUI.updateCommentText();
                            CrashAssistantGUI.showLogsAndDisableSimpleMode();
                        });
                    }
                    return;
                }
            }
            status = FAILED;
        }).start();
    }

    public static UUIDCheckStatus waitAndGetStatus() {
        if (!isStarted.get()) {
            startCheck();
        }

        long totalTimeout = 6000;
        long deadline = checkStartTime + totalTimeout;

        while (status == PROCESSING || status == UNDEFINED) {
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (status == PROCESSING || status == UNDEFINED) {
            return FAILED;
        }

        return status;
    }

    public static UUIDCheckStatus verifyUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return FAILED;
        }

        if (!uuid.matches("^[\\w-]+$")) {
            return FAILED;
        }

        String cleanUuid = uuid.replace("-", "");
        if (cleanUuid.length() != 32) {
            return FAILED;
        }

        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid + "?unsigned=false");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                try {
                    StringBuilder responseBuilder = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                    }
                    JsonObject json = JsonParser.parseString(responseBuilder.toString()).getAsJsonObject();
                    if (json.has("name")) {
                        String serverName = json.get("name").getAsString();
                        String actualName = getUsername();
                        if (actualName != null && !actualName.equalsIgnoreCase(serverName)) {
                            CrashAssistantApp.LOGGER.warn("UUID mismatch! mojang: {}, local: {}; assuming recent username change.", serverName, actualName);
                            // Prism is using random UUIDs in case of offline mod. But it is extremely unlikely what random UUID will be a valid one.
                            // But most cases here will be licensed players which recently changed username, and launcher haven't for whatever reason yet updated it.
                            return FAILED;
                        }
                    }
                } catch (Exception e) {
                    CrashAssistantApp.LOGGER.error("JSON parsing failed, defaulting to LICENSED", e);
                }

                return LICENSED;
            } else if (responseCode == 204 || responseCode == 404) {
                return PIRACY_OR_OFFLINE;
            } else {
                return FAILED;
            }
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to verify UUID: ", e);
            return FAILED;
        }
    }
}