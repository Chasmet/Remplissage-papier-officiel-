package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class SettingsActivity extends Activity {
    private static final String PREFS = "settings";
    private static final String STATE_PENDING_INSTALL = "pending_install";
    private static final int REQ_CREATE_BACKUP = 301;
    private static final int REQ_RESTORE_BACKUP = 302;

    private TextView tvUpdateStatus;
    private TextView tvUpdateDiagnostics;
    private TextView tvPercent;
    private TextView tvMcpStatus;
    private TextView tvBackupStatus;
    private ProgressBar progressUpdate;
    private Button btnDownloadUpdate;
    private Button btnInstallUpdate;
    private Button btnCheckUpdate;
    private EditText etMcpUrl;
    private EditText etMcpToken;

    private UpdateManager.UpdateInfo latestInfo;
    private File downloadedApk;
    private boolean pendingInstallAfterPermission;
    private boolean checkingUpdate;
    private boolean downloadingUpdate;
    private boolean installationOpening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView tvVersion = findViewById(R.id.tvVersion);
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        tvUpdateDiagnostics = findViewById(R.id.tvUpdateDiagnostics);
        tvPercent = findViewById(R.id.tvPercent);
        tvMcpStatus = findViewById(R.id.tvMcpStatus);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        progressUpdate = findViewById(R.id.progressUpdate);
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate);
        btnInstallUpdate = findViewById(R.id.btnInstallUpdate);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        etMcpUrl = findViewById(R.id.etMcpUrl);
        etMcpToken = findViewById(R.id.etMcpToken);

        if (savedInstanceState != null) {
            pendingInstallAfterPermission = savedInstanceState.getBoolean(STATE_PENDING_INSTALL, false);
        }

        tvVersion.setText("Version installée : " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        loadMcpSettings();

        downloadedApk = UpdateManager.getUpdateFile(this);
        validateExistingDownload();
        showInstallStatus();
        updateDiagnostics();

        findViewById(R.id.btnBackupData).setOnClickListener(v -> requestBackupDestination());
        findViewById(R.id.btnRestoreData).setOnClickListener(v -> requestBackupSource());
        btnCheckUpdate.setOnClickListener(v -> checkUpdate());
        btnDownloadUpdate.setOnClickListener(v -> downloadUpdate());
        btnInstallUpdate.setOnClickListener(v -> installUpdate());
        findViewById(R.id.btnSaveMcp).setOnClickListener(v -> saveMcpSettings());
        findViewById(R.id.btnTestMcp).setOnClickListener(v -> testMcp());

        checkUpdate();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PENDING_INSTALL, pendingInstallAfterPermission);
    }

    @Override
    protected void onResume() {
        super.onResume();
        installationOpening = false;
        downloadedApk = UpdateManager.getUpdateFile(this);
        showInstallStatus();
        updateDiagnostics();
        refreshUpdateButtons();

        if (pendingInstallAfterPermission && UpdateManager.canInstallPackages(this)) {
            pendingInstallAfterPermission = false;
            tvUpdateStatus.setText("Autorisation accordée. Préparation de la session d’installation Android…");
            installUpdate();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_CREATE_BACKUP) {
            try {
                DataBackupManager.write(this, uri);
                tvBackupStatus.setText("Sauvegarde créée. Gardez ce fichier : il permet de restaurer profil et brouillons après une réinstallation.");
                Toast.makeText(this, "Sauvegarde terminée", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                AppLog.write(this, "backupData", e);
                tvBackupStatus.setText("Sauvegarde impossible : " + safeMessage(e));
                Toast.makeText(this, "Sauvegarde impossible", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQ_RESTORE_BACKUP) {
            try {
                DataBackupManager.RestoreResult result = DataBackupManager.restore(this, uri);
                loadMcpSettings();
                tvBackupStatus.setText("Restauration terminée : " + result.profileItems
                        + " éléments de profil et " + result.draftItems + " éléments de brouillons restaurés.");
                Toast.makeText(this, "Données restaurées", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                AppLog.write(this, "restoreData", e);
                tvBackupStatus.setText("Restauration impossible : " + safeMessage(e));
                Toast.makeText(this, "Fichier de sauvegarde invalide", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestBackupDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, DataBackupManager.suggestedFileName());
        startActivityForResult(intent, REQ_CREATE_BACKUP);
    }

    private void requestBackupSource() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_RESTORE_BACKUP);
    }

    private void validateExistingDownload() {
        if (downloadedApk == null || !downloadedApk.exists() || downloadedApk.length() <= 0) {
            btnInstallUpdate.setEnabled(false);
            return;
        }

        UpdateManager.ApkValidation validation = UpdateManager.validateDownloadedApk(this, downloadedApk);
        if (validation.valid) {
            btnInstallUpdate.setEnabled(true);
            tvUpdateStatus.setText("Mise à jour téléchargée, vérifiée et prête à être installée.");
        } else {
            btnInstallUpdate.setEnabled(false);
            if (!validation.reinstallRequired) downloadedApk.delete();
            if (validation.reinstallRequired) {
                tvUpdateStatus.setText("Signature installée différente de la clé permanente : réinstallation nécessaire.");
            } else {
                tvUpdateStatus.setText(validation.message);
            }
        }
    }

    private void checkUpdate() {
        if (checkingUpdate || downloadingUpdate) return;
        checkingUpdate = true;
        refreshUpdateButtons();
        tvUpdateStatus.setText("Vérification en cours…");

        UpdateManager.checkLatest((info, error) -> runOnUiThread(() -> {
            checkingUpdate = false;
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                AppLog.write(this, "checkUpdate", error);
                tvUpdateStatus.setText("Vérification impossible : " + safeMessage(error));
                updateDiagnostics();
                refreshUpdateButtons();
                return;
            }
            latestInfo = info;
            if (info != null && UpdateManager.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                tvUpdateStatus.setText("Nouvelle version disponible : " + info.version);
            } else {
                tvUpdateStatus.setText("L’application est à jour (" + BuildConfig.VERSION_NAME + ").");
            }
            downloadedApk = UpdateManager.getUpdateFile(this);
            updateDiagnostics();
            refreshUpdateButtons();
        }));
    }

    private void downloadUpdate() {
        if (downloadingUpdate || checkingUpdate) return;
        if (latestInfo == null || latestInfo.apkUrl == null
                || !UpdateManager.isNewer(latestInfo.version, BuildConfig.VERSION_NAME)) {
            Toast.makeText(this, "Vérifiez d’abord les mises à jour", Toast.LENGTH_SHORT).show();
            return;
        }

        downloadingUpdate = true;
        UpdateManager.clearInstallStatus(this);
        progressUpdate.setProgress(0);
        tvPercent.setText("0 %");
        tvUpdateStatus.setText("Téléchargement et vérification en cours…");
        refreshUpdateButtons();

        UpdateManager.download(this, latestInfo.version, latestInfo.apkUrl, latestInfo.apkDigest,
                new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressUpdate.setProgress(percent);
                    tvPercent.setText(percent + " %");
                });
            }

            @Override
            public void onComplete(File file) {
                runOnUiThread(() -> {
                    downloadingUpdate = false;
                    if (isFinishing() || isDestroyed()) return;
                    downloadedApk = file;
                    progressUpdate.setProgress(100);
                    tvPercent.setText("100 %");

                    UpdateManager.ApkValidation validation = UpdateManager.validateDownloadedApk(SettingsActivity.this, file);
                    if (validation.valid) {
                        tvUpdateStatus.setText("APK contrôlé : package, version et signature permanente compatibles.");
                    } else if (validation.reinstallRequired) {
                        tvUpdateStatus.setText("APK valide, mais l’application installée n’utilise pas la clé permanente.");
                    } else {
                        tvUpdateStatus.setText("APK refusé : " + validation.message);
                        file.delete();
                    }
                    updateDiagnostics();
                    refreshUpdateButtons();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    downloadingUpdate = false;
                    AppLog.write(SettingsActivity.this, "downloadUpdate", error);
                    if (isFinishing() || isDestroyed()) return;
                    tvUpdateStatus.setText("Erreur téléchargement : " + safeMessage(error));
                    updateDiagnostics();
                    refreshUpdateButtons();
                });
            }
        });
    }

    private void installUpdate() {
        if (installationOpening || downloadingUpdate) return;
        try {
            downloadedApk = UpdateManager.getUpdateFile(this);
            UpdateManager.ApkValidation validation = UpdateManager.validateDownloadedApk(this, downloadedApk);
            updateDiagnostics();
            if (!validation.valid) {
                if (validation.reinstallRequired) {
                    tvUpdateStatus.setText("Android ne peut pas remplacer cette installation : signature installée non permanente.");
                } else {
                    tvUpdateStatus.setText("Installation bloquée : " + validation.message);
                }
                refreshUpdateButtons();
                return;
            }

            installationOpening = true;
            UpdateManager.InstallResult result = UpdateManager.install(this, downloadedApk);
            if (result == UpdateManager.InstallResult.PERMISSION_REQUIRED) {
                installationOpening = false;
                pendingInstallAfterPermission = true;
                tvUpdateStatus.setText("Autorisez les installations depuis cette application. La procédure reprendra automatiquement.");
            } else {
                tvUpdateStatus.setText("Session d’installation Android créée. Validez la mise à jour lorsqu’Android le demande.");
            }
            showInstallStatus();
        } catch (Exception e) {
            installationOpening = false;
            AppLog.write(this, "installUpdate", e);
            tvUpdateStatus.setText("Installation impossible : " + safeMessage(e));
            Toast.makeText(this, "Installation impossible : " + safeMessage(e), Toast.LENGTH_LONG).show();
            refreshUpdateButtons();
        }
    }

    private void showInstallStatus() {
        UpdateManager.InstallStatus status = UpdateManager.getInstallStatus(this);
        if (!status.isEmpty()) {
            String text = status.title;
            if (!status.detail.isEmpty()) text += " • " + status.detail;
            tvUpdateStatus.setText(text);
        }
    }

    private void updateDiagnostics() {
        File apk = downloadedApk;
        if (apk == null || !apk.exists()) apk = UpdateManager.getUpdateFile(this);
        tvUpdateDiagnostics.setText(UpdateManager.buildDiagnostics(this, apk)
                + "\nClé attendue : " + UpdateManager.expectedSignerShort());
    }

    private void refreshUpdateButtons() {
        boolean newer = latestInfo != null
                && latestInfo.apkUrl != null
                && UpdateManager.isNewer(latestInfo.version, BuildConfig.VERSION_NAME);
        btnCheckUpdate.setEnabled(!checkingUpdate && !downloadingUpdate);
        btnDownloadUpdate.setEnabled(!checkingUpdate && !downloadingUpdate && newer);

        File apk = downloadedApk != null ? downloadedApk : UpdateManager.getUpdateFile(this);
        boolean installable = false;
        if (!downloadingUpdate && apk.exists() && apk.length() > 0) {
            installable = UpdateManager.validateDownloadedApk(this, apk).valid;
        }
        btnInstallUpdate.setEnabled(installable && !installationOpening);
    }

    private void loadMcpSettings() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        etMcpUrl.setText(p.getString("mcpUrl", ""));
        etMcpToken.setText(p.getString("mcpToken", ""));
        if (!p.getString("mcpUrl", "").isEmpty()) {
            tvMcpStatus.setText("MCP configuré. Testez la connexion.");
        } else {
            tvMcpStatus.setText("MCP non configuré.");
        }
    }

    private void saveMcpSettings() {
        String url = etMcpUrl.getText().toString().trim();
        String token = etMcpToken.getText().toString().trim();
        if (!url.isEmpty() && !url.startsWith("https://")) {
            Toast.makeText(this, "Utilisez une URL MCP HTTPS", Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("mcpUrl", url)
                .putString("mcpToken", token)
                .apply();
        tvMcpStatus.setText(url.isEmpty() ? "MCP non configuré." : "MCP enregistré.");
    }

    private void testMcp() {
        String url = etMcpUrl.getText().toString().trim();
        String token = etMcpToken.getText().toString().trim();
        if (url.isEmpty()) {
            tvMcpStatus.setText("Renseignez l’URL du serveur MCP.");
            return;
        }
        tvMcpStatus.setText("Test MCP en cours…");
        McpClient.testConnection(url, token, (success, message) -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) tvMcpStatus.setText(message);
        }));
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "erreur inconnue";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
