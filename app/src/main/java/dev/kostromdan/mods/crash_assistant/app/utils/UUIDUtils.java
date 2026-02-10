package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UUIDUtils {
    public static UUIDCheckStatus status = UUIDCheckStatus.UNDEFINED;

    public static String getUUID() {
        if (Boot.MINECRAFT_LAUNCH_COMMAND == null || Boot.MINECRAFT_LAUNCH_COMMAND.isEmpty()) {
            return null;
        }

        Pattern UUID_PATTERN = Pattern.compile("--uuid[\\s=:,]+([^\\s,]+)");
        Matcher matcher = UUID_PATTERN.matcher(Boot.MINECRAFT_LAUNCH_COMMAND);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static UUIDCheckStatus verifyUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return UUIDCheckStatus.FAILED;
        }

        if (!uuid.matches("^[\\w-]+$")) {
            return UUIDCheckStatus.FAILED;
        }

        String cleanUuid = uuid.replace("-", "");
        if (cleanUuid.length() != 32) {
            return UUIDCheckStatus.FAILED;
        }

        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid + "?unsigned=false");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                return UUIDCheckStatus.LICENSED;
            } else if (responseCode == 204 || responseCode == 404) {
                return UUIDCheckStatus.PIRACY_OR_OFFLINE;
            } else {
                return UUIDCheckStatus.FAILED;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return UUIDCheckStatus.FAILED;
        }
    }
}