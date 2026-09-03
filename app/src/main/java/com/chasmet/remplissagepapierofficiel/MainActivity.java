package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
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
        btnDocuments.setOnClickListener(v -> Toast.makeText(this, "Mes documents sera enrichi dans la prochaine amélioration.", Toast.LENGTH_SHORT).show());
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }
}
