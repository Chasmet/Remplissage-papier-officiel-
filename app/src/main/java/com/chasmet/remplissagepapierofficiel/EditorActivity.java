package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EditorActivity extends Activity {
    private static final int REQ_PICK_PDF = 100;
    private static final int REQ_CREATE_PDF = 101;
    private static final int REQ_CREATE_PNG = 102;

    private PdfOverlayView pdfView;
    private TextView tvPage;
    private TextView tvPosition;
    private EditText etOverlayText;

    private Uri sourceUri;
    private ParcelFileDescriptor sourceDescriptor;
    private PdfRenderer renderer;
    private int pageIndex = 0;
    private float selectedX = 0.10f;
    private float selectedY = 0.10f;
    private final List<TextOverlay> overlays = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        pdfView = findViewById(R.id.pdfView);
        tvPage = findViewById(R.id.tvPage);
        tvPosition = findViewById(R.id.tvPosition);
        etOverlayText = findViewById(R.id.etOverlayText);

        pdfView.setOnPositionSelectedListener((x, y) -> {
            selectedX = x;
            selectedY = y;
            tvPosition.setText(String.format(java.util.Locale.FRANCE, "Position : %.1f %% / %.1f %%", x * 100f, y * 100f));
        });

        findViewById(R.id.btnChoosePdf).setOnClickListener(v -> choosePdf());
        findViewById(R.id.btnPrevPage).setOnClickListener(v -> changePage(-1));
        findViewById(R.id.btnNextPage).setOnClickListener(v -> changePage(1));
        findViewById(R.id.btnAddText).setOnClickListener(v -> addText());
        findViewById(R.id.btnUseName).setOnClickListener(v -> useProfileName());
        findViewById(R.id.btnUseAddress).setOnClickListener(v -> useProfileAddress());
        findViewById(R.id.btnClearPage).setOnClickListener(v -> clearCurrentPage());
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> requestPdfExport());
        findViewById(R.id.btnExportPng).setOnClickListener(v -> requestPngExport());
    }

    private void choosePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_PDF);
    }

    private void openPdf(Uri uri, Intent data) {
        closePdf();
        try {
            if (data != null) {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, flags);
                } catch (SecurityException ignored) {
                }
            }
            sourceDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (sourceDescriptor == null) throw new IOException("Document inaccessible");
            renderer = new PdfRenderer(sourceDescriptor);
            sourceUri = uri;
            pageIndex = 0;
            overlays.clear();
            renderCurrentPage();
        } catch (Exception e) {
            closePdf();
            Toast.makeText(this, "Impossible d’ouvrir ce PDF : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void changePage(int delta) {
        if (renderer == null) return;
        int next = pageIndex + delta;
        if (next < 0 || next >= renderer.getPageCount()) return;
        pageIndex = next;
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        if (renderer == null) return;
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(pageIndex);
            float factor = Math.min(2f, 1200f / Math.max(1f, page.getWidth()));
            int width = Math.max(1, Math.round(page.getWidth() * factor));
            int height = Math.max(1, Math.round(page.getHeight() * factor));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pdfView.setPage(bitmap, currentPageOverlays());
            tvPage.setText("Page " + (pageIndex + 1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            Toast.makeText(this, "Erreur d’affichage : " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (page != null) page.close();
        }
    }

    private List<TextOverlay> currentPageOverlays() {
        List<TextOverlay> result = new ArrayList<>();
        for (TextOverlay overlay : overlays) {
            if (overlay.pageIndex == pageIndex) result.add(overlay);
        }
        return result;
    }

    private void addText() {
        if (renderer == null) {
            Toast.makeText(this, "Choisissez d’abord un PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etOverlayText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Saisissez le texte à ajouter", Toast.LENGTH_SHORT).show();
            return;
        }
        overlays.add(new TextOverlay(pageIndex, selectedX, selectedY, text, 14f));
        pdfView.setOverlays(currentPageOverlays());
        etOverlayText.setText("");
    }

    private void useProfileName() {
        SharedPreferences p = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        String value = (p.getString("firstName", "") + " " + p.getString("lastName", "")).trim();
        etOverlayText.setText(value);
    }

    private void useProfileAddress() {
        SharedPreferences p = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        String line1 = p.getString("address", "").trim();
        String line2 = (p.getString("postalCode", "") + " " + p.getString("city", "")).trim();
        String value = (line1 + (line1.isEmpty() || line2.isEmpty() ? "" : " - ") + line2).trim();
        etOverlayText.setText(value);
    }

    private void clearCurrentPage() {
        Iterator<TextOverlay> iterator = overlays.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().pageIndex == pageIndex) iterator.remove();
        }
        pdfView.setOverlays(currentPageOverlays());
    }

    private void requestPdfExport() {
        if (renderer == null) {
            Toast.makeText(this, "Aucun PDF ouvert", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "document-rempli.pdf");
        startActivityForResult(intent, REQ_CREATE_PDF);
    }

    private void requestPngExport() {
        if (renderer == null) {
            Toast.makeText(this, "Aucun PDF ouvert", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "page-remplie-" + (pageIndex + 1) + ".png");
        startActivityForResult(intent, REQ_CREATE_PNG);
    }

    private void exportPdf(Uri target) {
        PdfDocument out = new PdfDocument();
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        try {
            for (int i = 0; i < renderer.getPageCount(); i++) {
                PdfRenderer.Page src = renderer.openPage(i);
                int width = src.getWidth();
                int height = src.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                src.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                src.close();

                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(width, height, i + 1).create();
                PdfDocument.Page dest = out.startPage(info);
                Canvas canvas = dest.getCanvas();
                canvas.drawBitmap(bitmap, 0f, 0f, null);
                for (TextOverlay overlay : overlays) {
                    if (overlay.pageIndex != i) continue;
                    textPaint.setTextSize(overlay.textSize);
                    canvas.drawText(overlay.text, overlay.x * width, overlay.y * height, textPaint);
                }
                out.finishPage(dest);
                bitmap.recycle();
            }

            try (OutputStream stream = getContentResolver().openOutputStream(target, "w")) {
                if (stream == null) throw new IOException("Destination inaccessible");
                out.writeTo(stream);
            }
            Toast.makeText(this, "PDF rempli enregistré", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur export PDF : " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            out.close();
        }
    }

    private void exportCurrentPagePng(Uri target) {
        PdfRenderer.Page page = null;
        Bitmap bitmap = null;
        try {
            page = renderer.openPage(pageIndex);
            int width = page.getWidth();
            int height = page.getHeight();
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            Canvas canvas = new Canvas(bitmap);
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.BLACK);
            for (TextOverlay overlay : currentPageOverlays()) {
                textPaint.setTextSize(overlay.textSize);
                canvas.drawText(overlay.text, overlay.x * width, overlay.y * height, textPaint);
            }
            try (OutputStream stream = getContentResolver().openOutputStream(target, "w")) {
                if (stream == null) throw new IOException("Destination inaccessible");
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            Toast.makeText(this, "PNG enregistré", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur export PNG : " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (page != null) page.close();
            if (bitmap != null) bitmap.recycle();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_PICK_PDF) {
            openPdf(uri, data);
        } else if (requestCode == REQ_CREATE_PDF) {
            exportPdf(uri);
        } else if (requestCode == REQ_CREATE_PNG) {
            exportCurrentPagePng(uri);
        }
    }

    private void closePdf() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (sourceDescriptor != null) {
            try {
                sourceDescriptor.close();
            } catch (IOException ignored) {
            }
            sourceDescriptor = null;
        }
        sourceUri = null;
    }

    @Override
    protected void onDestroy() {
        closePdf();
        super.onDestroy();
    }
}
