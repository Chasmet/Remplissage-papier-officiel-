package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class DiagnosticsActivity extends Activity {
    private TextView tvBridgeDiagnostic;
    private TextView tvLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        tvBridgeDiagnostic = findViewById(R.id.tvBridgeDiagnostic);
        tvLogs = findViewById(R.id.tvLogs);

        findViewById(R.id.btnRefreshLogs).setOnClickListener(v -> refresh());
        findViewById(R.id.btnCopyLogs).setOnClickListener(v -> copy());
        findViewById(R.id.btnClearLogs).setOnClickListener(v -> {
            AppLog.clear(this);
            AppLog.write(this, "Journal effacé manuellement", null);
            refresh();
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        tvBridgeDiagnostic.setText(McpBridgeState.diagnostic(this));
        String logs = AppLog.readRecent(this, 80_000);
        tvLogs.setText(logs.isEmpty() ? "Aucun événement enregistré." : logs);
    }

    private void copy() {
        String text = McpBridgeState.diagnostic(this)
                + "\n\n===== JOURNAL =====\n"
                + AppLog.readRecent(this, 80_000);
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic MCP", text));
            Toast.makeText(this, "Diagnostic copié", Toast.LENGTH_SHORT).show();
        }
    }
}
