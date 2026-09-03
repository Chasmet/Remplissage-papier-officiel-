package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class SettingsActivity extends Activity {
    private static final String PREFS = "settings";

    private TextView tvUpdateStatus;
    private TextView tvPercent;
    private TextView tvMcpStatus;
    private ProgressBar progressUpdate;
    private Button btnDownloadUpdate;
    private Button btnInstallUpdate;
    private EditText etMcpUrl;
    private EditText etMcpToken;

    private UpdateManager.UpdateInfo latestInfo;
    private File downloadedApk;
    private boolean pendingInstallAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView tvVersion = findViewById(R.id.tvVersion);
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        tvPercent = findViewById(R.id.tvPercent);
        tvMcpStatus = findViewById(R.id.tvMcpStatus);
        progressUpdate = findViewById(R.id.progressUpdate);
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate);
        btnInstallUpdate = findViewById(R.id.btnInstallUpdate);
        etMcpUrl = findViewById(R.id.etMcpUrl);
        etMcpToken = findViewById(R.id.etMcpToken);

        tvVersion.setText("Version installée : " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        loadMcpSettings();

        downloadedApk = UpdateManager.getUpdateFile(this);
        validateExistingDownload();

        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> checkUpdate());
        btnDownloadUpdate.setOnClickListener(v -> downloadUpdate());
        btnInstallUpdate.setOnClickListener(v -> installUpdate());
        findViewById(R.id.btnSaveMcp).setOnClickListener(v -> saveMcpSettings());
        findViewById(R.id.btnTestMcp).setOnClickListener(v -> testMcp());

        checkUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingInstallAfterPermission && UpdateManager.canInstallPackages(this)) {
            pendingInstallAfterPermission = false;
            tvUpdateStatus.setText("Autorisation accordée. Ouverture de l’installation…");
            installUpdate();
        }
    }

    private void validateExistingDownload() {
        if (downloadedApk == null || !downloadedApk.exists() || downloadedApk.length() <= 0) {
            btnInstallUpdate.setEnabled(false);
            return;
        }

        UpdateManager.ApkValidation validation = UpdateManager.validateDownloadedApk(this, downloadedApk);
        if (validation.valid) {
            btnInstallUpdate.setEnabled(true);
            tvUpdateStatus.setText("Mise à jour téléchargée et signature vérifiée. Prête à être installée.");
        } else {
            btnInstallUpdate.setEnabled(false);
            if (validation.reinstallRequired) {
                tvUpdateStatus.setText("Ancienne signature détectée. Réinstallation unique nécessaire avant les futures MAJ internes.");
            } else {
                tvUpdateStatus.setText(validation.message);
            }
        }
    }

    private void checkUpdate() {
        tvUpdateStatus.setText("Vérification en cours…");
        btnDownloadUpdate.setEnabled(false);
        UpdateManager.checkLatest((info, error) -> runOnUiThread(() -> {
            if (error != null) {
                tvUpdateStatus.setText("Vérification impossible : " + error.getMessage());
                return;
            }
            latestInfo = info;
            if (UpdateManager.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                tvUpdateStatus.setText("Nouvelle version disponible : " + info.version + " • APK signé vérifié avant installation");
                btnDownloadUpdate.setEnabled(true);
            } else {
                tvUpdateStatus.setText("L’application est à jour (" + BuildConfig.VERSION_NAME + ").");
            }
        }));
    }

    private void downloadUpdate() {
        if (latestInfo == null || latestInfo.apkUrl == null) {
            Toast.makeText(this, "Vérifiez d’abord les mises à jour", Toast.LENGTH_SHORT).show();
            return;
        }

        btnDownloadUpdate.setEnabled(false);
        btnInstallUpdate.setEnabled(false);
        progressUpdate.setProgress(0);
        tvPercent.setText("0 %");
        tvUpdateStatus.setText("Téléchargement et vérification en cours…");

        UpdateManager.download(this, latestInfo.apkUrl, latestInfo.apkDigest, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                runOnUiThread(() -> {
                    progressUpdate.setProgress(percent);
                    tvPercent.setText(percent + " %");
                });
            }

            @Override
            public void onComplete(File file) {
                runOnUiThread(() -> {
                    downloadedApk = file;
                    progressUpdate.setProgress(100);
                    tvPercent.setText("100 %");

                    UpdateManager.ApkValidation validation = UpdateManager.validateDownloadedApk(SettingsActivity.this, file);
                    if (validation.valid) {
                        tvUpdateStatus.setText("Téléchargement terminé. Signature compatible : installation autorisée.");
                        btnInstallUpdate.setEnabled(true);
                    } else if (validation.reinstallRequired) {
                        tvUpdateStatus.setText("Cette APK utilise la nouvelle signature permanente. L’ancienne application doit être désinstallée une seule fois avant de l’installer.");
                        btnInstallUpdate.setEnabled(false);
                    } else {
                        tvUpdateStatus.setText("APK refusé : " + validation.message);
                        btnInstallUpdate.setEnabled(false);
                    }
                    btnDownloadUpdate.setEnabled(true);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    tvUpdateStatus.setText("Erreur de téléchargement/vérification : " + error.getMessage());
                    btnDownloadUpdate.setEnabled(true);
                    btnInstallUpdate.setEnabled(false);
                });
            }
        });
    }

    private void installUpdate() {
        try {
            if (downloadedApk == null) downloadedApk = UpdateManager.getUpdateFile(this);
            UpdateManager.ApkValidation validation = UpdateManager.validateDownloadedApk(this, downloadedApk);
            if (!validation.valid) {
                if (validation.reinstallRequired) {
                    tvUpdateStatus.setText("Installation bloquée : ancienne signature Android. Réinstallation unique nécessaire pour passer à la clé permanente.");
                } else {
                    tvUpdateStatus.setText("Installation bloquée : " + validation.message);
                }
                return;
            }

            UpdateManager.InstallResult result = UpdateManager.install(this, downloadedApk);
            if (result == UpdateManager.InstallResult.PERMISSION_REQUIRED) {
                pendingInstallAfterPermission = true;
                tvUpdateStatus.setText("Autorisez ‘Installer des applications inconnues’. L’installation reprendra automatiquement au retour.");
            } else {
                tvUpdateStatus.setText("Installation Android ouverte. Validez la mise à jour.");
            }
        } catch (Exception e) {
            tvUpdateStatus.setText("Installation impossible : " + e.getMessage());
            Toast.makeText(this, "Installation impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadMcpSettings() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        etMcpUrl.setText(p.getString("mcpUrl", ""));
        etMcpToken.setText(p.getString("mcpToken", ""));
        if (!p.getString("mcpUrl", "").isEmpty()) {
            tvMcpStatus.setText("MCP configuré. Testez la connexion.");
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
        McpClient.testConnection(url, token,
                (success, message) -> runOnUiThread(() -> tvMcpStatus.setText(message)));
    }
}
