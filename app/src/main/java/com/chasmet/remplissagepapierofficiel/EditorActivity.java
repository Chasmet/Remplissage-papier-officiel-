package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditorActivity extends Activity {
    private static final int REQ_PICK_PDF = 100;
    private static final int REQ_CREATE_PDF = 101;
    private static final int REQ_CREATE_PNG = 102;
    private static final String DRAFT_PREFS = "editor_drafts";

    private PdfOverlayView pdfView;
    private TextView tvPage;
    private TextView tvPosition;
    private TextView tvFieldStatus;
    private TextView tvTextSize;
    private EditText etOverlayText;
    private Button btnResetZoom;

    private Uri sourceUri;
    private ParcelFileDescriptor sourceDescriptor;
    private PdfRenderer renderer;
    private int pageIndex = 0;
    private float selectedX = 0.10f;
    private float selectedY = 0.10f;
    private float currentTextSize = 14f;
    private String draftKey;

    private final List<TextOverlay> overlays = new ArrayList<>();
    private final Map<Integer, List<FormField>> detectedFieldsByPage = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        pdfView = findViewById(R.id.pdfView);
        tvPage = findViewById(R.id.tvPage);
        tvPosition = findViewById(R.id.tvPosition);
        tvFieldStatus = findViewById(R.id.tvFieldStatus);
        tvTextSize = findViewById(R.id.tvTextSize);
        etOverlayText = findViewById(R.id.etOverlayText);
        btnResetZoom = findViewById(R.id.btnResetZoom);

        pdfView.setOnPositionSelectedListener((x, y) -> {
            selectedX = x;
            selectedY = y;
            tvPosition.setText(String.format(Locale.FRANCE,
                    "Position : %.1f %% / %.1f %% • pincer pour zoomer", x * 100f, y * 100f));
        });

        pdfView.setOnFieldSelectedListener((index, field) -> {
            int count = pdfView.getDetectedFieldCount();
            tvFieldStatus.setText((index + 1) + " / " + count);
            tvPosition.setText("Champ détecté " + (index + 1) + " sur " + count
                    + " • saisissez puis AJOUTER");
        });

        pdfView.setOnZoomChangedListener(zoom ->
                btnResetZoom.setText(Math.round(zoom * 100f) + "%"));

        findViewById(R.id.btnChoosePdf).setOnClickListener(v -> choosePdf());
        findViewById(R.id.btnPrevPage).setOnClickListener(v -> changePage(-1));
        findViewById(R.id.btnNextPage).setOnClickListener(v -> changePage(1));
        findViewById(R.id.btnDetectFields).setOnClickListener(v -> redetectCurrentPage());
        findViewById(R.id.btnPrevField).setOnClickListener(v -> pdfView.selectPreviousField());
        findViewById(R.id.btnNextField).setOnClickListener(v -> pdfView.selectNextField());
        btnResetZoom.setOnClickListener(v -> pdfView.resetZoom());

        findViewById(R.id.btnAddText).setOnClickListener(v -> addText());
        findViewById(R.id.btnUndo).setOnClickListener(v -> undoLastOnCurrentPage());
        findViewById(R.id.btnUseFirstName).setOnClickListener(v -> useProfileValue("firstName"));
        findViewById(R.id.btnUseLastName).setOnClickListener(v -> useProfileValue("lastName"));
        findViewById(R.id.btnUseName).setOnClickListener(v -> useProfileName());
        findViewById(R.id.btnUseBirthDate).setOnClickListener(v -> useProfileValue("birthDate"));
        findViewById(R.id.btnUseToday).setOnClickListener(v -> useToday());
        findViewById(R.id.btnUseAddress).setOnClickListener(v -> useProfileAddress());
        findViewById(R.id.btnUsePhone).setOnClickListener(v -> useProfileValue("phone"));
        findViewById(R.id.btnUseEmail).setOnClickListener(v -> useProfileValue("email"));
        findViewById(R.id.btnTextSmaller).setOnClickListener(v -> changeTextSize(-1f));
        findViewById(R.id.btnTextLarger).setOnClickListener(v -> changeTextSize(1f));
        findViewById(R.id.btnClearPage).setOnClickListener(v -> clearCurrentPage());
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> requestPdfExport());
        findViewById(R.id.btnExportPng).setOnClickListener(v -> requestPngExport());

        updateTextSizeLabel();
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
                int flags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, flags);
                } catch (SecurityException ignored) {
                }
            }

            sourceDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (sourceDescriptor == null) throw new IOException("Document inaccessible");
            renderer = new PdfRenderer(sourceDescriptor);
            sourceUri = uri;
            draftKey = buildDraftKey(uri);
            pageIndex = 0;
            overlays.clear();
            detectedFieldsByPage.clear();
            loadDraft();
            if (renderer.getPageCount() > 0) {
                pageIndex = Math.max(0, Math.min(pageIndex, renderer.getPageCount() - 1));
            }
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
        saveDraft();
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        if (renderer == null) return;
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(pageIndex);

            // Large display raster so pinch-to-zoom stays sharp on official forms.
            float factor = Math.min(3f, 1800f / Math.max(1f, page.getWidth()));
            factor = Math.max(1.5f, factor);
            int width = Math.max(1, Math.round(page.getWidth() * factor));
            int height = Math.max(1, Math.round(page.getHeight() * factor));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            List<FormField> fields = detectedFieldsByPage.get(pageIndex);
            if (fields == null) {
                fields = FormFieldDetector.detect(bitmap, pageIndex);
                detectedFieldsByPage.put(pageIndex, fields);
            }

            pdfView.setPage(bitmap, currentPageOverlays());
            pdfView.setDetectedFields(fields);
            tvPage.setText("Page " + (pageIndex + 1) + " / " + renderer.getPageCount());
            updateFieldStatus(fields.size());

            if (!fields.isEmpty()) {
                pdfView.selectNextField();
            } else {
                selectedX = 0.10f;
                selectedY = 0.10f;
                tvPosition.setText("Aucune ligne détectée • touchez librement le document pour écrire.");
            }
        } catch (OutOfMemoryError error) {
            Toast.makeText(this,
                    "Mémoire insuffisante pour cette page. Réessayez après avoir fermé d’autres applications.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur d’affichage : " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (page != null) page.close();
        }
    }

    private void redetectCurrentPage() {
        if (renderer == null) {
            Toast.makeText(this, "Choisissez d’abord un PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        detectedFieldsByPage.remove(pageIndex);
        tvFieldStatus.setText("Analyse…");
        renderCurrentPage();
        int count = pdfView.getDetectedFieldCount();
        Toast.makeText(this, count + " zone(s) à remplir détectée(s)", Toast.LENGTH_SHORT).show();
    }

    private void updateFieldStatus(int count) {
        if (count <= 0) {
            tvFieldStatus.setText("0 champ");
        } else {
            tvFieldStatus.setText(count + (count > 1 ? " champs" : " champ"));
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

        overlays.add(new TextOverlay(pageIndex, selectedX, selectedY, text, currentTextSize));
        pdfView.setOverlays(currentPageOverlays());
        etOverlayText.setText("");
        saveDraft();

        if (pdfView.getDetectedFieldCount() > 0) {
            pdfView.selectNextField();
        }
    }

    private void undoLastOnCurrentPage() {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i).pageIndex == pageIndex) {
                overlays.remove(i);
                pdfView.setOverlays(currentPageOverlays());
                saveDraft();
                Toast.makeText(this, "Dernier ajout annulé", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "Rien à annuler sur cette page", Toast.LENGTH_SHORT).show();
    }

    private void changeTextSize(float delta) {
        currentTextSize = Math.max(9f, Math.min(28f, currentTextSize + delta));
        updateTextSizeLabel();
    }

    private void updateTextSizeLabel() {
        if (tvTextSize != null) tvTextSize.setText(Math.round(currentTextSize) + " pt");
    }

    private void useProfileValue(String key) {
        SharedPreferences p = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        String value = p.getString(key, "").trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Cette information n’est pas renseignée dans Mon profil", Toast.LENGTH_SHORT).show();
        }
        etOverlayText.setText(value);
        etOverlayText.setSelection(etOverlayText.length());
    }

    private void useProfileName() {
        SharedPreferences p = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        String value = (p.getString("firstName", "") + " " + p.getString("lastName", "")).trim();
        etOverlayText.setText(value);
        etOverlayText.setSelection(etOverlayText.length());
    }

    private void useProfileAddress() {
        SharedPreferences p = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        String line1 = p.getString("address", "").trim();
        String line2 = (p.getString("postalCode", "") + " " + p.getString("city", "")).trim();
        String value = (line1 + (line1.isEmpty() || line2.isEmpty() ? "" : " - ") + line2).trim();
        etOverlayText.setText(value);
        etOverlayText.setSelection(etOverlayText.length());
    }

    private void useToday() {
        String value = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
        etOverlayText.setText(value);
        etOverlayText.setSelection(etOverlayText.length());
    }

    private void clearCurrentPage() {
        Iterator<TextOverlay> iterator = overlays.iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            if (iterator.next().pageIndex == pageIndex) {
                iterator.remove();
                changed = true;
            }
        }
        pdfView.setOverlays(currentPageOverlays());
        if (changed) saveDraft();
    }

    private String buildDraftKey(Uri uri) {
        return "draft_" + Integer.toHexString(uri.toString().hashCode());
    }

    private void loadDraft() {
        if (draftKey == null) return;
        SharedPreferences prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE);
        String json = prefs.getString(draftKey, null);
        pageIndex = prefs.getInt(draftKey + "_page", 0);
        if (json == null || json.isEmpty()) return;

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                overlays.add(new TextOverlay(
                        item.optInt("page", 0),
                        (float) item.optDouble("x", 0.1),
                        (float) item.optDouble("y", 0.1),
                        item.optString("text", ""),
                        (float) item.optDouble("size", 14.0)
                ));
            }
            if (!overlays.isEmpty()) {
                Toast.makeText(this, "Brouillon précédent restauré", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {
            // A damaged draft must never prevent opening the source PDF.
        }
    }

    private void saveDraft() {
        if (draftKey == null) return;
        try {
            JSONArray array = new JSONArray();
            for (TextOverlay overlay : overlays) {
                JSONObject item = new JSONObject();
                item.put("page", overlay.pageIndex);
                item.put("x", overlay.x);
                item.put("y", overlay.y);
                item.put("text", overlay.text);
                item.put("size", overlay.textSize);
                array.put(item);
            }
            getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(draftKey, array.toString())
                    .putInt(draftKey + "_page", pageIndex)
                    .apply();
        } catch (Exception ignored) {
        }
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
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        try {
            for (int i = 0; i < renderer.getPageCount(); i++) {
                PdfRenderer.Page src = renderer.openPage(i);
                int pageWidth = src.getWidth();
                int pageHeight = src.getHeight();
                float renderScale = Math.min(3f, Math.max(2f, 1800f / Math.max(1f, pageWidth)));
                int bitmapWidth = Math.max(1, Math.round(pageWidth * renderScale));
                int bitmapHeight = Math.max(1, Math.round(pageHeight * renderScale));
                Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                src.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                src.close();

                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create();
                PdfDocument.Page dest = out.startPage(info);
                Canvas canvas = dest.getCanvas();
                canvas.drawBitmap(bitmap, null,
                        new RectF(0f, 0f, pageWidth, pageHeight), bitmapPaint);

                for (TextOverlay overlay : overlays) {
                    if (overlay.pageIndex != i) continue;
                    textPaint.setTextSize(overlay.textSize);
                    canvas.drawText(overlay.text,
                            overlay.x * pageWidth,
                            overlay.y * pageHeight,
                            textPaint);
                }
                out.finishPage(dest);
                bitmap.recycle();
            }

            try (OutputStream stream = getContentResolver().openOutputStream(target, "w")) {
                if (stream == null) throw new IOException("Destination inaccessible");
                out.writeTo(stream);
            }
            Toast.makeText(this, "PDF rempli enregistré en haute qualité", Toast.LENGTH_LONG).show();
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
            int pageWidth = page.getWidth();
            int pageHeight = page.getHeight();
            float scale = Math.min(3f, Math.max(2f, 1800f / Math.max(1f, pageWidth)));
            int width = Math.max(1, Math.round(pageWidth * scale));
            int height = Math.max(1, Math.round(pageHeight * scale));
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

            Canvas canvas = new Canvas(bitmap);
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.BLACK);
            for (TextOverlay overlay : currentPageOverlays()) {
                textPaint.setTextSize(overlay.textSize * scale);
                canvas.drawText(overlay.text, overlay.x * width, overlay.y * height, textPaint);
            }

            try (OutputStream stream = getContentResolver().openOutputStream(target, "w")) {
                if (stream == null) throw new IOException("Destination inaccessible");
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            Toast.makeText(this, "PNG haute qualité enregistré", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur export PNG : " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (page != null) page.close();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
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

    @Override
    protected void onPause() {
        if (renderer != null) saveDraft();
        super.onPause();
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
        if (pdfView != null) pdfView.setPage(null, null);
        sourceUri = null;
        draftKey = null;
        detectedFieldsByPage.clear();
    }

    @Override
    protected void onDestroy() {
        closePdf();
        super.onDestroy();
    }
}
