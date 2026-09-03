package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateManager {
    private static final String LATEST_RELEASE = "https://api.github.com/repos/Chasmet/Remplissage-papier-officiel-/releases/latest";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static class UpdateInfo {
        public final String version;
        public final String apkUrl;
        public final String releaseName;

        public UpdateInfo(String version, String apkUrl, String releaseName) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.releaseName = releaseName;
        }
    }

    public interface CheckCallback {
        void onResult(UpdateInfo info, Exception error);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(File file);
        void onError(Exception error);
    }

    private UpdateManager() {
    }

    public static void checkLatest(CheckCallback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_RELEASE).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "RemplissagePapierOfficiel/1.0");
                int code = connection.getResponseCode();
                if (code != 200) throw new IllegalStateException("GitHub HTTP " + code);
                String json;
                try (InputStream input = connection.getInputStream()) {
                    json = readAll(input);
                }
                JSONObject release = new JSONObject(json);
                String tag = release.optString("tag_name", "");
                String version = tag.startsWith("v") ? tag.substring(1) : tag;
                String name = release.optString("name", tag);
                String apkUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                if (version.isEmpty() || apkUrl == null) throw new IllegalStateException("Release APK introuvable");
                callback.onResult(new UpdateInfo(version, apkUrl, name), null);
            } catch (Exception e) {
                callback.onResult(null, e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static boolean isNewer(String remote, String local) {
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        int max = Math.max(r.length, l.length);
        for (int i = 0; i < max; i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }

    private static int[] parseVersion(String value) {
        String clean = value == null ? "0" : value.replaceAll("[^0-9.]", "");
        String[] parts = clean.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    public static File getUpdateFile(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "remplissage-papier-officiel-update.apk");
    }

    public static void download(Context context, String url, DownloadCallback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            File target = getUpdateFile(context);
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "RemplissagePapierOfficiel/1.0");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("Téléchargement HTTP " + code);

                int total = connection.getContentLength();
                long downloaded = 0;
                int lastPercent = -1;
                byte[] buffer = new byte[32 * 1024];
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target, false)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        downloaded += read;
                        int percent = total > 0 ? (int) Math.min(100, downloaded * 100L / total) : 0;
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            callback.onProgress(percent);
                        }
                    }
                    output.flush();
                }
                callback.onProgress(100);
                callback.onComplete(target);
            } catch (Exception e) {
                if (target.exists()) target.delete();
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static void install(Activity activity, File apk) {
        if (apk == null || !apk.exists()) throw new IllegalStateException("APK téléchargé introuvable");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permissionIntent);
            return;
        }

        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static String readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toString("UTF-8");
    }
}
