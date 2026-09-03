package com.chasmet.remplissagepapierofficiel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public final class DataBackupManager {
    private static final String FORMAT = "remplissage-papier-officiel.backup.v1";
    private static final int MAX_BACKUP_BYTES = 5 * 1024 * 1024;

    public static final class RestoreResult {
        public final int profileItems;
        public final int draftItems;
        public final boolean mcpUrlRestored;

        RestoreResult(int profileItems, int draftItems, boolean mcpUrlRestored) {
            this.profileItems = profileItems;
            this.draftItems = draftItems;
            this.mcpUrlRestored = mcpUrlRestored;
        }
    }

    private DataBackupManager() {
    }

    public static String suggestedFileName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(new Date());
        return "remplissage-papier-sauvegarde-" + stamp + ".json";
    }

    public static void write(Context context, Uri target) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("createdAt", System.currentTimeMillis());
        root.put("appVersion", BuildConfig.VERSION_NAME);
        root.put("profile", preferencesToJson(context.getSharedPreferences(ProfileActivity.PREFS, Context.MODE_PRIVATE)));
        root.put("drafts", preferencesToJson(context.getSharedPreferences("editor_drafts", Context.MODE_PRIVATE)));

        JSONObject safeSettings = new JSONObject();
        String mcpUrl = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("mcpUrl", "");
        safeSettings.put("mcpUrl", mcpUrl == null ? "" : mcpUrl);
        root.put("settings", safeSettings);
        root.put("securityNote", "Le jeton MCP n'est jamais exporte.");

        byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = context.getContentResolver().openOutputStream(target, "w")) {
            if (output == null) throw new IllegalStateException("Destination inaccessible");
            output.write(bytes);
            output.flush();
        }
    }

    public static RestoreResult restore(Context context, Uri source) throws Exception {
        byte[] bytes = readLimited(context, source);
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (!FORMAT.equals(root.optString("format", ""))) {
            throw new IllegalArgumentException("Fichier de sauvegarde non reconnu");
        }

        int profileCount = restorePreferences(
                context.getSharedPreferences(ProfileActivity.PREFS, Context.MODE_PRIVATE),
                root.optJSONObject("profile"), true);
        int draftCount = restorePreferences(
                context.getSharedPreferences("editor_drafts", Context.MODE_PRIVATE),
                root.optJSONObject("drafts"), false);

        boolean mcpUrlRestored = false;
        JSONObject settings = root.optJSONObject("settings");
        if (settings != null && settings.has("mcpUrl")) {
            String mcpUrl = settings.optString("mcpUrl", "").trim();
            if (mcpUrl.isEmpty() || mcpUrl.startsWith("https://")) {
                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("mcpUrl", mcpUrl)
                        .apply();
                mcpUrlRestored = !mcpUrl.isEmpty();
            }
        }

        return new RestoreResult(profileCount, draftCount, mcpUrlRestored);
    }

    private static JSONObject preferencesToJson(SharedPreferences preferences) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Integer
                    || value instanceof Long || value instanceof Boolean
                    || value instanceof Float) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private static int restorePreferences(SharedPreferences preferences, JSONObject source,
                                          boolean stringsOnly) throws Exception {
        if (source == null) return 0;
        SharedPreferences.Editor editor = preferences.edit();
        int count = 0;
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null || key.length() > 160) continue;
            Object value = source.opt(key);
            if (value == null || value == JSONObject.NULL) continue;

            if (stringsOnly) {
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                    count++;
                }
                continue;
            }

            if (value instanceof String) {
                editor.putString(key, (String) value);
                count++;
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
                count++;
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
                count++;
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
                count++;
            } else if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (Math.floor(number) == number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                    editor.putInt(key, (int) number);
                } else {
                    editor.putFloat(key, (float) number);
                }
                count++;
            }
        }
        if (!editor.commit()) throw new IllegalStateException("Restauration impossible");
        return count;
    }

    private static byte[] readLimited(Context context, Uri source) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("Sauvegarde inaccessible");
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BACKUP_BYTES) {
                    throw new IllegalArgumentException("Sauvegarde trop volumineuse");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
