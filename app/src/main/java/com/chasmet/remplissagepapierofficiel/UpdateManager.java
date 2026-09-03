package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateManager {
    private static final String LATEST_RELEASE = "https://api.github.com/repos/Chasmet/Remplissage-papier-officiel-/releases/latest";
    private static final String EXPECTED_PACKAGE = "com.chasmet.remplissagepapierofficiel";
    private static final String PERMANENT_SIGNER_SHA256 = "B5BBDB2521ACE477BBC1AA2F431ADDE7061268A38E267331FCB67D1E97640AFD";
    private static final String UPDATE_PREFS = "update_state";
    private static final String KEY_LAST_APK = "last_apk";
    private static final String KEY_INSTALL_TITLE = "install_title";
    private static final String KEY_INSTALL_DETAIL = "install_detail";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static class UpdateInfo {
        public final String version;
        public final String apkUrl;
        public final String releaseName;
        public final String apkDigest;

        public UpdateInfo(String version, String apkUrl, String releaseName, String apkDigest) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.releaseName = releaseName;
            this.apkDigest = apkDigest;
        }
    }

    public static class ApkValidation {
        public final boolean valid;
        public final boolean signatureCompatible;
        public final boolean reinstallRequired;
        public final String message;
        public final long installedVersionCode;
        public final long candidateVersionCode;
        public final String installedSigner;
        public final String candidateSigner;
        public final String candidatePackage;

        ApkValidation(boolean valid, boolean signatureCompatible, boolean reinstallRequired,
                      String message, long installedVersionCode, long candidateVersionCode,
                      String installedSigner, String candidateSigner, String candidatePackage) {
            this.valid = valid;
            this.signatureCompatible = signatureCompatible;
            this.reinstallRequired = reinstallRequired;
            this.message = message;
            this.installedVersionCode = installedVersionCode;
            this.candidateVersionCode = candidateVersionCode;
            this.installedSigner = installedSigner;
            this.candidateSigner = candidateSigner;
            this.candidatePackage = candidatePackage;
        }
    }

    public static class InstallStatus {
        public final String title;
        public final String detail;

        InstallStatus(String title, String detail) {
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
        }

        public boolean isEmpty() {
            return title.isEmpty() && detail.isEmpty();
        }
    }

    public enum InstallResult {
        STARTED,
        PERMISSION_REQUIRED
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
                connection.setRequestProperty("User-Agent", "RemplissagePapierOfficiel/1.5");
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
                String apkDigest = null;

                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            apkDigest = asset.optString("digest", null);
                            break;
                        }
                    }
                }

                if (version.isEmpty() || apkUrl == null) {
                    throw new IllegalStateException("Release APK introuvable");
                }
                callback.onResult(new UpdateInfo(version, apkUrl, name, apkDigest), null);
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
        String stored = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_APK, "");
        if (stored == null || stored.trim().isEmpty()) {
            return new File(getUpdateDirectory(context), "no-update-downloaded.apk");
        }
        File file = new File(stored);
        File dir = getUpdateDirectory(context);
        try {
            String canonicalDir = dir.getCanonicalPath() + File.separator;
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalDir)) {
                return new File(dir, "no-update-downloaded.apk");
            }
        } catch (Exception e) {
            return new File(dir, "no-update-downloaded.apk");
        }
        return file;
    }

    private static File getUpdateDirectory(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = new File(context.getFilesDir(), "updates");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File createVersionedTarget(Context context, String version) {
        String safeVersion = version == null ? "unknown" : version.replaceAll("[^0-9A-Za-z._-]", "_");
        return new File(getUpdateDirectory(context),
                "remplissage-papier-officiel-v" + safeVersion + "-" + System.currentTimeMillis() + ".apk");
    }

    public static void download(Context context, String version, String url, String expectedDigest,
                                DownloadCallback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            File target = createVersionedTarget(context, version);
            File partial = new File(target.getAbsolutePath() + ".part");
            try {
                if (url == null || !url.startsWith("https://")) {
                    throw new SecurityException("URL de mise à jour non HTTPS");
                }
                if (partial.exists()) partial.delete();

                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "RemplissagePapierOfficiel/1.5");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("Téléchargement HTTP " + code);
                }

                long total = connection.getContentLength();
                long downloaded = 0;
                int lastPercent = -1;
                byte[] buffer = new byte[32 * 1024];
                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial, false)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        downloaded += read;
                        int percent = total > 0 ? (int) Math.min(100, downloaded * 100L / total) : 0;
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            callback.onProgress(percent);
                        }
                    }
                    output.getFD().sync();
                }

                String actualDigest = "sha256:" + toHex(digest.digest()).toLowerCase(Locale.ROOT);
                if (expectedDigest != null && !expectedDigest.trim().isEmpty()
                        && !actualDigest.equalsIgnoreCase(expectedDigest.trim())) {
                    throw new SecurityException("Empreinte SHA-256 de l’APK incorrecte");
                }

                if (target.exists()) target.delete();
                if (!partial.renameTo(target)) {
                    copyFile(partial, target);
                    partial.delete();
                }

                context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_LAST_APK, target.getAbsolutePath())
                        .apply();
                cleanupOldDownloads(context, target);
                callback.onProgress(100);
                callback.onComplete(target);
            } catch (Exception e) {
                if (partial.exists()) partial.delete();
                if (target.exists()) target.delete();
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static void cleanupOldDownloads(Context context, File keep) {
        File dir = getUpdateDirectory(context);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file == null || file.equals(keep)) continue;
            String name = file.getName().toLowerCase(Locale.ROOT);
            if ((name.startsWith("remplissage-papier-officiel-v") && name.endsWith(".apk"))
                    || name.endsWith(".apk.part")) {
                file.delete();
            }
        }
    }

    public static ApkValidation validateDownloadedApk(Context context, File apk) {
        long installedCode = -1;
        String installedSigner = "";
        try {
            PackageManager pm = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            installedCode = getVersionCode(installed);
            Set<String> installedSigners = signerFingerprints(installed);
            installedSigner = joinFingerprints(installedSigners);

            if (apk == null || !apk.exists() || apk.length() < 100_000L) {
                return new ApkValidation(false, false, false,
                        "APK téléchargé absent ou incomplet.", installedCode, -1,
                        installedSigner, "", "");
            }

            PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (candidate == null) {
                return new ApkValidation(false, false, false,
                        "Android ne reconnaît pas le fichier téléchargé comme un APK valide.",
                        installedCode, -1, installedSigner, "", "");
            }

            long candidateCode = getVersionCode(candidate);
            Set<String> candidateSigners = signerFingerprints(candidate);
            String candidateSigner = joinFingerprints(candidateSigners);
            String candidatePackage = candidate.packageName == null ? "" : candidate.packageName;

            if (!EXPECTED_PACKAGE.equals(candidatePackage)
                    || !context.getPackageName().equals(candidatePackage)) {
                return new ApkValidation(false, false, false,
                        "Le package de la mise à jour ne correspond pas à l’application installée.",
                        installedCode, candidateCode, installedSigner, candidateSigner, candidatePackage);
            }

            if (candidateCode <= installedCode) {
                return new ApkValidation(false, true, false,
                        "Cette version n’est pas plus récente que l’application installée.",
                        installedCode, candidateCode, installedSigner, candidateSigner, candidatePackage);
            }

            if (installedSigners.isEmpty() || candidateSigners.isEmpty()) {
                return new ApkValidation(false, false, false,
                        "Impossible de vérifier la signature Android de la mise à jour.",
                        installedCode, candidateCode, installedSigner, candidateSigner, candidatePackage);
            }

            if (!candidateSigners.contains(PERMANENT_SIGNER_SHA256)) {
                return new ApkValidation(false, false, false,
                        "APK refusé : il n’utilise pas la clé permanente autorisée.",
                        installedCode, candidateCode, installedSigner, candidateSigner, candidatePackage);
            }

            if (!installedSigners.contains(PERMANENT_SIGNER_SHA256)) {
                return new ApkValidation(false, false, true,
                        "L’installation actuelle n’utilise pas la clé permanente. Une réinstallation est nécessaire.",
                        installedCode, candidateCode, installedSigner, candidateSigner, candidatePackage);
            }

            if (!installedSigners.equals(candidateSigners)) {
                return new ApkValidation(false, false, true,
                        "Les certificats Android de l’installation et de la mise à jour sont différents.",
                        installedCode, candidateCode, installedSigner, candidateSigner, candidatePackage);
            }

            return new ApkValidation(true, true, false,
                    "APK vérifié : package, version et signature permanente sont compatibles.",
                    installedCode, candidateCode, installedSigner, candidateSigner, candidatePackage);
        } catch (Exception e) {
            return new ApkValidation(false, false, false,
                    "Vérification APK impossible : " + safeMessage(e), installedCode, -1,
                    installedSigner, "", "");
        }
    }

    public static String buildDiagnostics(Context context, File apk) {
        try {
            PackageManager pm = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            String installedSigner = joinFingerprints(signerFingerprints(installed));
            StringBuilder out = new StringBuilder();
            out.append("Installée : ")
                    .append(BuildConfig.VERSION_NAME)
                    .append(" / code ")
                    .append(getVersionCode(installed))
                    .append("\nPackage : ")
                    .append(context.getPackageName())
                    .append("\nSignature installée : ")
                    .append(shortFingerprint(installedSigner));

            if (apk != null && apk.exists() && apk.length() > 0) {
                ApkValidation validation = validateDownloadedApk(context, apk);
                out.append("\nAPK téléchargé : code ")
                        .append(validation.candidateVersionCode)
                        .append("\nSignature APK : ")
                        .append(shortFingerprint(validation.candidateSigner))
                        .append("\nCompatibilité : ")
                        .append(validation.valid ? "OK" : validation.message);
            } else {
                out.append("\nAPK téléchargé : aucun");
            }
            return out.toString();
        } catch (Exception e) {
            return "Diagnostic indisponible : " + safeMessage(e);
        }
    }

    public static boolean canInstallPackages(Activity activity) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    public static void requestInstallPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permissionIntent);
        }
    }

    public static InstallResult install(Activity activity, File apk) throws Exception {
        ApkValidation validation = validateDownloadedApk(activity, apk);
        if (!validation.valid) {
            throw new IllegalStateException(validation.message);
        }

        if (!canInstallPackages(activity)) {
            requestInstallPermission(activity);
            return InstallResult.PERMISSION_REQUIRED;
        }

        clearInstallStatus(activity);
        PackageInstaller packageInstaller = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        params.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= 33) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }

        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = null;
        boolean committed = false;
        try {
            session = packageInstaller.openSession(sessionId);
            try (InputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                session.fsync(output);
            }

            Intent resultIntent = new Intent(activity, InstallResultReceiver.class);
            resultIntent.setAction(activity.getPackageName() + ".UPDATE_INSTALL_RESULT");
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) pendingFlags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    activity, sessionId, resultIntent, pendingFlags);

            saveInstallStatus(activity,
                    "Installation préparée",
                    "Session Android " + sessionId + " • attente de confirmation.");
            session.commit(pendingIntent.getIntentSender());
            committed = true;
            return InstallResult.STARTED;
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            if (!committed) {
                try {
                    packageInstaller.abandonSession(sessionId);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void saveInstallStatus(Context context, String title, String detail) {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTALL_TITLE, title == null ? "" : title)
                .putString(KEY_INSTALL_DETAIL, detail == null ? "" : detail)
                .apply();
    }

    public static InstallStatus getInstallStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE);
        return new InstallStatus(
                prefs.getString(KEY_INSTALL_TITLE, ""),
                prefs.getString(KEY_INSTALL_DETAIL, ""));
    }

    public static void clearInstallStatus(Context context) {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_INSTALL_TITLE)
                .remove(KEY_INSTALL_DETAIL)
                .apply();
    }

    public static String expectedSignerShort() {
        return shortFingerprint(PERMANENT_SIGNER_SHA256);
    }

    private static long getVersionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }

    private static Set<String> signerFingerprints(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) return new HashSet<>();
            signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }

        Set<String> result = new HashSet<>();
        if (signatures == null) return result;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            if (signature == null) continue;
            md.reset();
            result.add(toHex(md.digest(signature.toByteArray())).toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private static String joinFingerprints(Set<String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) out.append(',');
            out.append(sorted.get(i));
        }
        return out.toString();
    }

    private static String shortFingerprint(String value) {
        if (value == null || value.isEmpty()) return "inconnue";
        String clean = value.replace(":", "").toUpperCase(Locale.ROOT);
        if (clean.length() <= 20) return clean;
        return clean.substring(0, 10) + "…" + clean.substring(clean.length() - 10);
    }

    private static void copyFile(File source, File target) throws Exception {
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.getFD().sync();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static String readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toString("UTF-8");
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "erreur inconnue";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
