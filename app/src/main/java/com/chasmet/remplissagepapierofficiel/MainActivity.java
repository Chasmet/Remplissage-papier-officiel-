package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;

public class MainActivity extends Activity {
    private static final String SETTINGS_PREFS = "settings";
    private static final String UI_PREFS = "mcp_ui";
    private static final String LAST_OPENED_CHAT_JOB = "last_opened_chat_job";
    private static final String LAST_OPENED_CHAT_NAME = "last_opened_chat_name";
    private static final String LAST_OPENED_CHAT_PATH = "last_opened_chat_path";

    private boolean updateDialogShown = false;
    private boolean inboxCheckRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnNewDocument = findViewById(R.id.btnNewDocument);
        Button btnImportPdf = findViewById(R.id.btnImportPdf);
        Button btnDocuments = findViewById(R.id.btnDocuments);
        Button btnProfile = findViewById(R.id.btnProfile);
        Button btnSettings = findViewById(R.id.btnSettings);

        btnNewDocument.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        btnImportPdf.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        btnDocuments.setOnClickListener(v -> openLastChatGptDocumentOrCheckInbox());
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        checkForUpdateOnLaunch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkChatGptInbox();
    }

    private void checkChatGptInbox() {
        if (inboxCheckRunning) return;

        SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "").trim();
        String token = settings.getString("mcpToken", "").trim();
        if (endpoint.isEmpty()) return;

        inboxCheckRunning = true;
        McpClient.getInbox(endpoint, token, new McpClient.InboxCallback() {
            @Override
            public void onInbox(JSONObject document) {
                runOnUiThread(() -> {
                    inboxCheckRunning = false;
                    if (isFinishing() || isDestroyed()) return;

                    String jobId = document.optString("job_id", "").trim();
                    String name = document.optString("name", "document.pdf");
                    String downloadUrl = document.optString("download_url", "").trim();
                    if (jobId.isEmpty() || downloadUrl.isEmpty()) return;

                    String lastOpened = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                            .getString(LAST_OPENED_CHAT_JOB, "");
                    if (jobId.equals(lastOpened)) return;

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Document reçu de ChatGPT")
                            .setMessage(name + "\n\nLe PDF est prêt à être ouvert dans Remplissage Papier.")
                            .setPositiveButton("OUVRIR", (dialog, which) ->
                                    downloadChatGptDocument(jobId, name, downloadUrl))
                            .setNegativeButton("PLUS TARD", null)
                            .show();
                });
            }

            @Override
            public void onEmpty() {
                runOnUiThread(() -> inboxCheckRunning = false);
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> inboxCheckRunning = false);
            }
        });
    }

    private void downloadChatGptDocument(String jobId, String name, String downloadUrl) {
        Toast.makeText(this, "Téléchargement du PDF…", Toast.LENGTH_SHORT).show();
        File target = new File(new File(getFilesDir(), "mcp-inbox"), "chatgpt-" + jobId + ".pdf");

        McpClient.downloadPdf(downloadUrl, target, (success, message) -> runOnUiThread(() -> {
            if (!success) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                return;
            }

            try {
                Uri uri = FileProvider.getUriForFile(
                        MainActivity.this,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        target);

                getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                        .edit()
                        .putString(LAST_OPENED_CHAT_JOB, jobId)
                        .putString(LAST_OPENED_CHAT_NAME, name)
                        .putString(LAST_OPENED_CHAT_PATH, target.getAbsolutePath())
                        .apply();

                Intent intent = new Intent(MainActivity.this, EditorActivity.class);
                intent.setData(uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra(EditorActivity.EXTRA_MCP_JOB_ID, jobId);
                intent.putExtra(EditorActivity.EXTRA_MCP_DOCUMENT_NAME, name);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this,
                        "Impossible d’ouvrir le PDF : " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void openLastChatGptDocumentOrCheckInbox() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        String jobId = prefs.getString(LAST_OPENED_CHAT_JOB, "");
        String name = prefs.getString(LAST_OPENED_CHAT_NAME, "document.pdf");
        String path = prefs.getString(LAST_OPENED_CHAT_PATH, "");

        if (jobId != null && !jobId.isEmpty() && path != null && !path.isEmpty()) {
            File file = new File(path);
            if (file.isFile()) {
                try {
                    Uri uri = FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            file);
                    Intent intent = new Intent(this, EditorActivity.class);
                    intent.setData(uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra(EditorActivity.EXTRA_MCP_JOB_ID, jobId);
                    intent.putExtra(EditorActivity.EXTRA_MCP_DOCUMENT_NAME, name);
                    startActivity(intent);
                    return;
                } catch (Exception ignored) {
                }
            }
        }

        Toast.makeText(this, "Recherche d’un document ChatGPT…", Toast.LENGTH_SHORT).show();
        checkChatGptInbox();
    }

    private void checkForUpdateOnLaunch() {
        UpdateManager.checkLatest((info, error) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || error != null || info == null) return;
            if (!UpdateManager.isNewer(info.version, BuildConfig.VERSION_NAME)) return;
            if (updateDialogShown) return;

            updateDialogShown = true;
            new AlertDialog.Builder(this)
                    .setTitle("Mise à jour disponible")
                    .setMessage("La version " + info.version + " est disponible. Version installée : " + BuildConfig.VERSION_NAME + ".")
                    .setPositiveButton("METTRE À JOUR", (dialog, which) ->
                            startActivity(new Intent(this, SettingsActivity.class)))
                    .setNegativeButton("PLUS TARD", null)
                    .setOnDismissListener(dialog -> updateDialogShown = false)
                    .show();
        }));
    }
}
