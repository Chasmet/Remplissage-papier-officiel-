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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class EditorActivity extends Activity {
    private static final int REQ_PICK_PDF = 100;
    private static final int REQ_CREATE_PDF = 101;
    private static final int REQ_CREATE_PNG = 102;
    private static final String DRAFT_PREFS = "editor_drafts";
    private static final String STATE_URI = "source_uri";
    private static final String STATE_PAGE = "page_index";
    private static final String STATE_TEXT = "overlay_text";
    private static final String STATE_TEXT_SIZE = "text_size";

    private PdfOverlayView pdfView;
    private TextView tvPage;
    private TextView tvPosition;
    private TextView tvFieldStatus;
    private TextView tvTextSize;
    private EditText etOverlayText;
    private Button btnResetZoom;
    private Button btnChoosePdf;
    private Button btnDetectFields;
    private Button btnExportPdf;
    private Button btnExportPng;

    private Uri sourceUri;
    private ParcelFileDescriptor sourceDescriptor;
    private PdfRenderer renderer;
    private int pageIndex = 0;
    private float selectedX = 0.10f;
    private float selectedY = 0.10f;
    private float currentTextSize = 14f;
    private String draftKey;

    private final Object rendererLock = new Object();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private volatile boolean destroyed;
    private volatile boolean exportBusy;

    private final List<TextOverlay> overlays = new ArrayList<>();
    private final Map<Integer, List<FormField>> detectedFieldsByPage = new HashMap<>();

    private static class RenderedPage {
        final Bitmap bitmap;
        final int pageWidth;
        final int pageHeight;

        RenderedPage(Bitmap bitmap, int pageWidth, int pageHeight) {
            this.bitmap = bitmap;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }
    }

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
        btnChoosePdf = findViewById(R.id.btnChoosePdf);
        btnDetectFields = findViewById(R.id.btnDetectFields);
        btnExportPdf = findViewById(R.id.btnExportPdf);
        btnExportPng = findViewById(R.id.btnExportPng);

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

        btnChoosePdf.setOnClickListener(v -> choosePdf());
        findViewById(R.id.btnPrevPage).setOnClickListener(v -> changePage(-1));
        findViewById(R.id.btnNextPage).setOnClickListener(v -> changePage(1));
        btnDetectFields.setOnClickListener(v -> redetectCurrentPage());
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
        btnExportPdf.setOnClickListener(v -> requestPdfExport());
        btnExportPng.setOnClickListener(v -> requestPngExport());

        if (savedInstanceState != null) {
            currentTextSize = savedInstanceState.getFloat(STATE_TEXT_SIZE, 14f);
            etOverlayText.setText(savedInstanceState.getString(STATE_TEXT, ""));
        }
        updateTextSizeLabel();

        if (savedInstanceState != null) {
            String savedUri = savedInstanceState.getString(STATE_URI, "");
            if (!savedUri.isEmpty()) {
                int savedPage = savedInstanceState.getInt(STATE_PAGE, 0);
                openPdf(Uri.parse(savedUri), null, savedPage);
            }
        }
    }

    private void choosePdf() {
        if (exportBusy) {
            Toast.makeText(this, "Export en cours, attendez sa fin", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_PDF);
    }

    private void openPdf(Uri uri, Intent data) {
        openPdf(uri, data, -1);
    }

    private void openPdf(Uri uri, Intent data, int requestedPage) {
        closePdf();
        try {
            if (data != null) {
                int flags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (flags != 0) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, flags);
                    } catch (SecurityException ignored) {
                    }
                }
            }

            ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor == null) throw new IOException("Document inaccessible");
            PdfRenderer openedRenderer = new PdfRenderer(descriptor);
            if (openedRenderer.getPageCount() <= 0) {
                openedRenderer.close();
                descriptor.close();
                throw new IOException("PDF vide");
            }

            synchronized (rendererLock) {
                sourceDescriptor = descriptor;
                renderer = openedRenderer;
            }
            sourceUri = uri;
            draftKey = buildDraftKey(uri);
            pageIndex = 0;
            overlays.clear();
            synchronized (detectedFieldsByPage) {
                detectedFieldsByPage.clear();
            }
            loadDraft();
            int count = getPageCountSafe();
            if (requestedPage >= 0) pageIndex = requestedPage;
            pageIndex = Math.max(0, Math.min(pageIndex, Math.max(0, count - 1)));
            renderCurrentPage();
        } catch (Exception e) {
            AppLog.write(this, "openPdf", e);
            closePdf();
            Toast.makeText(this, "Impossible d’ouvrir ce PDF : " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private int getPageCountSafe() {
        synchronized (rendererLock) {
            try {
                return renderer == null ? 0 : renderer.getPageCount();
            } catch (Exception e) {
                AppLog.write(this, "getPageCount", e);
                return 0;
            }
        }
    }

    private void changePage(int delta) {
        if (exportBusy) return;
        int count = getPageCountSafe();
        if (count <= 0) return;
        int next = pageIndex + delta;
        if (next < 0 || next >= count) return;
        pageIndex = next;
        saveDraft(false);
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        final int requestedPage = pageIndex;
        final int generation = renderGeneration.incrementAndGet();
        if (getPageCountSafe() <= 0) return;

        tvFieldStatus.setText("Chargement…");
        btnDetectFields.setEnabled(false);

        worker.execute(() -> {
            Bitmap bitmap = null;
            try {
                RenderedPage rendered = renderPage(requestedPage, false);
                bitmap = rendered.bitmap;
                if (generation != renderGeneration.get() || destroyed) {
                    recycle(bitmap);
                    return;
                }

                List<FormField> fields;
                synchronized (detectedFieldsByPage) {
                    List<FormField> cached = detectedFieldsByPage.get(requestedPage);
                    fields = cached == null ? null : new ArrayList<>(cached);
                }

                if (fields == null) {
                    try {
                        fields = FormFieldDetector.detect(bitmap, requestedPage);
                    } catch (OutOfMemoryError oom) {
                        AppLog.write(this, "fieldDetectionOOM page=" + requestedPage, oom);
                        fields = new ArrayList<>();
                    } catch (Exception detectionError) {
                        AppLog.write(this, "fieldDetection page=" + requestedPage, detectionError);
                        fields = new ArrayList<>();
                    }
                    synchronized (detectedFieldsByPage) {
                        detectedFieldsByPage.put(requestedPage, new ArrayList<>(fields));
                    }
                }

                if (generation != renderGeneration.get() || destroyed) {
                    recycle(bitmap);
                    return;
                }

                Bitmap finalBitmap = bitmap;
                List<FormField> finalFields = fields;
                bitmap = null; // Ownership moves to PdfOverlayView on the UI thread.
                runOnUiThread(() -> {
                    if (destroyed || generation != renderGeneration.get() || requestedPage != pageIndex) {
                        recycle(finalBitmap);
                        return;
                    }
                    pdfView.setPage(finalBitmap, currentPageOverlays());
                    pdfView.setDetectedFields(finalFields);
                    int count = getPageCountSafe();
                    tvPage.setText("Page " + (requestedPage + 1) + " / " + count);
                    updateFieldStatus(finalFields.size());
                    btnDetectFields.setEnabled(true);

                    if (!finalFields.isEmpty()) {
                        pdfView.selectNextField();
                    } else {
                        selectedX = 0.10f;
                        selectedY = 0.10f;
                        tvPosition.setText("Aucune ligne détectée • touchez librement le document pour écrire.");
                    }
                });
            } catch (OutOfMemoryError oom) {
                AppLog.write(this, "renderPageOOM page=" + requestedPage, oom);
                recycle(bitmap);
                runOnUiThread(() -> {
                    if (!destroyed && generation == renderGeneration.get()) {
                        btnDetectFields.setEnabled(true);
                        tvFieldStatus.setText("Mémoire limitée");
                        Toast.makeText(this,
                                "Page très lourde : affichage impossible avec la mémoire disponible.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                AppLog.write(this, "renderPage page=" + requestedPage, e);
                recycle(bitmap);
                runOnUiThread(() -> {
                    if (!destroyed && generation == renderGeneration.get()) {
                        btnDetectFields.setEnabled(true);
                        tvFieldStatus.setText("Erreur d’affichage");
                        Toast.makeText(this, "Erreur d’affichage : " + safeMessage(e), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private RenderedPage renderPage(int index, boolean printMode) throws Exception {
        synchronized (rendererLock) {
            if (renderer == null) throw new IOException("PDF fermé");
            if (index < 0 || index >= renderer.getPageCount()) throw new IOException("Page invalide");
            PdfRenderer.Page page = renderer.openPage(index);
            try {
                int pageWidth = page.getWidth();
                int pageHeight = page.getHeight();
                float desired = printMode ? 2.15f : 2.45f;
                float maxDimension = printMode ? 1800f : 1650f;
                float dimensionFactor = maxDimension / Math.max(1f, Math.max(pageWidth, pageHeight));
                float target = Math.max(1f, Math.min(desired, dimensionFactor));

                long maxMemory = Runtime.getRuntime().maxMemory();
                long maxPixels = Math.max(1_800_000L, Math.min(7_000_000L, maxMemory / 18L));
                double pixelsAtOne = (double) pageWidth * (double) pageHeight;
                if (pixelsAtOne > 0) {
                    float memoryFactor = (float) Math.sqrt(maxPixels / pixelsAtOne);
                    target = Math.min(target, Math.max(1f, memoryFactor));
                }

                float[] attempts = new float[]{target, Math.min(target, 1.60f), 1.20f, 1.0f};
                OutOfMemoryError lastOom = null;
                for (float scale : attempts) {
                    Bitmap bitmap = null;
                    try {
                        int width = Math.max(1, Math.round(pageWidth * scale));
                        int height = Math.max(1, Math.round(pageHeight * scale));
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null,
                                printMode ? PdfRenderer.Page.RENDER_MODE_FOR_PRINT : PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        return new RenderedPage(bitmap, pageWidth, pageHeight);
                    } catch (OutOfMemoryError oom) {
                        lastOom = oom;
                        recycle(bitmap);
                        System.gc();
                    }
                }
                if (lastOom != null) throw lastOom;
                throw new IOException("Impossible de rendre la page");
            } finally {
                page.close();
            }
        }
    }

    private void redetectCurrentPage() {
        if (getPageCountSafe() <= 0) {
            Toast.makeText(this, "Choisissez d’abord un PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        synchronized (detectedFieldsByPage) {
            detectedFieldsByPage.remove(pageIndex);
        }
        tvFieldStatus.setText("Analyse…");
        renderCurrentPage();
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
        if (getPageCountSafe() <= 0) {
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
        saveDraft(false);

        if (pdfView.getDetectedFieldCount() > 0) {
            pdfView.selectNextField();
        }
    }

    private void undoLastOnCurrentPage() {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i).pageIndex == pageIndex) {
                overlays.remove(i);
                pdfView.setOverlays(currentPageOverlays());
                saveDraft(false);
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
        if (changed) saveDraft(false);
    }

    private String buildDraftKey(Uri uri) {
        return "draft_" + Integer.toHexString(uri.toString().hashCode());
    }

    private void loadDraft() {
        if (draftKey == null) return;
        SharedPreferences prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE);
        pageIndex = prefs.getInt(draftKey + "_page", 0);
        String primary = prefs.getString(draftKey, null);
        String backup = prefs.getString(draftKey + "_backup", null);

        if (!restoreDraftJson(primary)) {
            restoreDraftJson(backup);
        }
        if (!overlays.isEmpty()) {
            Toast.makeText(this, "Brouillon précédent restauré", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean restoreDraftJson(String json) {
        if (json == null || json.isEmpty()) return false;
        try {
            JSONArray array = new JSONArray(json);
            List<TextOverlay> restored = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String text = item.optString("text", "");
                if (text.length() > 5000) continue;
                restored.add(new TextOverlay(
                        Math.max(0, item.optInt("page", 0)),
                        clamp01((float) item.optDouble("x", 0.1)),
                        clamp01((float) item.optDouble("y", 0.1)),
                        text,
                        Math.max(9f, Math.min(28f, (float) item.optDouble("size", 14.0)))
                ));
            }
            overlays.clear();
            overlays.addAll(restored);
            return true;
        } catch (Exception e) {
            AppLog.write(this, "restoreDraft", e);
            return false;
        }
    }

    private void saveDraft(boolean immediate) {
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

            SharedPreferences prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE);
            String previous = prefs.getString(draftKey, null);
            SharedPreferences.Editor edit = prefs.edit();
            if (previous != null && !previous.isEmpty()) {
                edit.putString(draftKey + "_backup", previous);
            }
            edit.putString(draftKey, array.toString())
                    .putInt(draftKey + "_page", pageIndex);
            if (immediate) {
                edit.commit();
            } else {
                edit.apply();
            }
        } catch (Exception e) {
            AppLog.write(this, "saveDraft", e);
        }
    }

    private void requestPdfExport() {
        if (getPageCountSafe() <= 0) {
            Toast.makeText(this, "Aucun PDF ouvert", Toast.LENGTH_SHORT).show();
            return;
        }
        if (exportBusy) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "document-rempli.pdf");
        startActivityForResult(intent, REQ_CREATE_PDF);
    }

    private void requestPngExport() {
        if (getPageCountSafe() <= 0) {
            Toast.makeText(this, "Aucun PDF ouvert", Toast.LENGTH_SHORT).show();
            return;
        }
        if (exportBusy) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "page-remplie-" + (pageIndex + 1) + ".png");
        startActivityForResult(intent, REQ_CREATE_PNG);
    }

    private void exportPdf(Uri target) {
        if (!beginExport("Export PDF en cours…")) return;
        final List<TextOverlay> overlaySnapshot = new ArrayList<>(overlays);
        worker.execute(() -> {
            PdfDocument out = new PdfDocument();
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.BLACK);
            Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            try {
                int count = getPageCountSafe();
                if (count <= 0) throw new IOException("PDF source fermé");

                for (int i = 0; i < count; i++) {
                    RenderedPage rendered = renderPage(i, true);
                    Bitmap bitmap = rendered.bitmap;
                    PdfDocument.Page dest = null;
                    try {
                        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                                rendered.pageWidth, rendered.pageHeight, i + 1).create();
                        dest = out.startPage(info);
                        Canvas canvas = dest.getCanvas();
                        canvas.drawBitmap(bitmap, null,
                                new RectF(0f, 0f, rendered.pageWidth, rendered.pageHeight), bitmapPaint);

                        for (TextOverlay overlay : overlaySnapshot) {
                            if (overlay.pageIndex != i) continue;
                            textPaint.setTextSize(overlay.textSize);
                            canvas.drawText(overlay.text,
                                    overlay.x * rendered.pageWidth,
                                    overlay.y * rendered.pageHeight,
                                    textPaint);
                        }
                        out.finishPage(dest);
                        dest = null;
                    } finally {
                        recycle(bitmap);
                        if (dest != null) {
                            try {
                                out.finishPage(dest);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                try (OutputStream stream = getContentResolver().openOutputStream(target, "w")) {
                    if (stream == null) throw new IOException("Destination inaccessible");
                    out.writeTo(stream);
                    stream.flush();
                }
                finishExport(true, "PDF rempli enregistré");
            } catch (OutOfMemoryError oom) {
                AppLog.write(this, "exportPdfOOM", oom);
                finishExport(false, "Mémoire insuffisante pendant l’export PDF");
            } catch (Exception e) {
                AppLog.write(this, "exportPdf", e);
                finishExport(false, "Erreur export PDF : " + safeMessage(e));
            } finally {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void exportCurrentPagePng(Uri target) {
        if (!beginExport("Export PNG en cours…")) return;
        final int exportPage = pageIndex;
        final List<TextOverlay> overlaySnapshot = new ArrayList<>(currentPageOverlays());
        worker.execute(() -> {
            Bitmap bitmap = null;
            try {
                RenderedPage rendered = renderPage(exportPage, true);
                bitmap = rendered.bitmap;
                Canvas canvas = new Canvas(bitmap);
                Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setColor(Color.BLACK);
                float scaleX = bitmap.getWidth() / (float) Math.max(1, rendered.pageWidth);
                float scaleY = bitmap.getHeight() / (float) Math.max(1, rendered.pageHeight);
                float textScale = Math.min(scaleX, scaleY);

                for (TextOverlay overlay : overlaySnapshot) {
                    textPaint.setTextSize(overlay.textSize * textScale);
                    canvas.drawText(overlay.text,
                            overlay.x * bitmap.getWidth(),
                            overlay.y * bitmap.getHeight(), textPaint);
                }

                try (OutputStream stream = getContentResolver().openOutputStream(target, "w")) {
                    if (stream == null) throw new IOException("Destination inaccessible");
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new IOException("Encodage PNG impossible");
                    }
                    stream.flush();
                }
                finishExport(true, "PNG enregistré");
            } catch (OutOfMemoryError oom) {
                AppLog.write(this, "exportPngOOM", oom);
                finishExport(false, "Mémoire insuffisante pendant l’export PNG");
            } catch (Exception e) {
                AppLog.write(this, "exportPng", e);
                finishExport(false, "Erreur export PNG : " + safeMessage(e));
            } finally {
                recycle(bitmap);
            }
        });
    }

    private boolean beginExport(String message) {
        if (exportBusy) {
            Toast.makeText(this, "Un export est déjà en cours", Toast.LENGTH_SHORT).show();
            return false;
        }
        exportBusy = true;
        btnExportPdf.setEnabled(false);
        btnExportPng.setEnabled(false);
        btnChoosePdf.setEnabled(false);
        tvPosition.setText(message);
        return true;
    }

    private void finishExport(boolean success, String message) {
        runOnUiThread(() -> {
            exportBusy = false;
            btnExportPdf.setEnabled(true);
            btnExportPng.setEnabled(true);
            btnChoosePdf.setEnabled(true);
            tvPosition.setText(message);
            Toast.makeText(this, message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        });
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (sourceUri != null) outState.putString(STATE_URI, sourceUri.toString());
        outState.putInt(STATE_PAGE, pageIndex);
        outState.putString(STATE_TEXT, etOverlayText == null ? "" : etOverlayText.getText().toString());
        outState.putFloat(STATE_TEXT_SIZE, currentTextSize);
        if (draftKey != null) saveDraft(true);
    }

    @Override
    protected void onPause() {
        if (draftKey != null) saveDraft(true);
        super.onPause();
    }

    private void closePdf() {
        renderGeneration.incrementAndGet();
        synchronized (rendererLock) {
            if (renderer != null) {
                try {
                    renderer.close();
                } catch (Exception e) {
                    AppLog.write(this, "closeRenderer", e);
                }
                renderer = null;
            }
            if (sourceDescriptor != null) {
                try {
                    sourceDescriptor.close();
                } catch (IOException e) {
                    AppLog.write(this, "closeDescriptor", e);
                }
                sourceDescriptor = null;
            }
        }
        if (pdfView != null) pdfView.setPage(null, null);
        sourceUri = null;
        draftKey = null;
        synchronized (detectedFieldsByPage) {
            detectedFieldsByPage.clear();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        renderGeneration.incrementAndGet();
        if (draftKey != null) saveDraft(true);
        worker.shutdownNow();
        closePdf();
        super.onDestroy();
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "erreur inconnue";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
