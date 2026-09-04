package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class EditorActivity extends Activity {
    public static final String EXTRA_MCP_JOB_ID = "mcp_job_id";
    public static final String EXTRA_MCP_DOCUMENT_NAME = "mcp_document_name";

    private static final int REQ_PICK_PDF = 100;
    private static final int REQ_CREATE_PDF = 101;
    private static final int REQ_CREATE_PNG = 102;
    private static final String DRAFT_PREFS = "editor_drafts";
    private static final String STATE_URI = "source_uri";
    private static final String STATE_PAGE = "page_index";
    private static final String STATE_TEXT = "overlay_text";
    private static final String STATE_TEXT_SIZE = "text_size";
    private static final String SETTINGS_PREFS = "settings";
    private static final String MCP_JOB_SUFFIX = "_mcp_job";
    private static final String MCP_JOB_VERSION_SUFFIX = "_mcp_job_version";
    private static final String MCP_COMMAND_SUFFIX = "_mcp_last_command";
    private static final long MCP_POLL_INTERVAL_MS = 2500L;
    private static final long MCP_BACKGROUND_BRIDGE_MS = 10L * 60L * 1000L;
    private static final int MAX_TEXT_PER_PAGE = 6000;
    private static final int MAX_TEXT_BLOCKS_PER_PAGE = 180;

    private PdfOverlayView pdfView;
    private TextView tvPage;
    private TextView tvPosition;
    private TextView tvFieldStatus;
    private TextView tvTextSize;
    private TextView tvChatGptStatus;
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
    private String mcpJobId = "";
    private String pendingInboundJobId = "";
    private String currentDocumentNameOverride = "";
    private boolean inboundNeedsContextSync = false;
    private volatile boolean mcpBusy;
    private boolean mcpForeground;
    private long mcpBackgroundUntilElapsed;
    private boolean bridgeReceiverRegistered;

    private final BroadcastReceiver mcpBridgeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !McpBridgeService.ACTION_UPDATED.equals(intent.getAction())) return;
            String jobId = intent.getStringExtra(McpBridgeService.EXTRA_JOB_ID);
            if (jobId == null || !jobId.equals(mcpJobId)) return;

            refreshMcpStatusUi();
            List<TextOverlay> updated = McpBridgeStore.loadOverlays(
                    EditorActivity.this, jobId);
            overlays.clear();
            overlays.addAll(updated);
            saveDraft(true);
            renderCurrentPage();
            tvPosition.setText("ChatGPT a mis à jour le document • contrôle visuel synchronisé.");
        }
    };

    private final Runnable mcpStatusUiRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !mcpForeground) return;
            refreshMcpStatusUi();
            mcpHandler.postDelayed(this, 1000L);
        }
    };

    private final Handler mcpHandler = new Handler(Looper.getMainLooper());
    private final Runnable mcpPollRunnable = new Runnable() {
        @Override
        public void run() {
            boolean bridgeActive = mcpForeground
                    || SystemClock.elapsedRealtime() < mcpBackgroundUntilElapsed;
            if (destroyed || !bridgeActive) return;

            if (sourceUri != null && getPageCountSafe() > 0 && !mcpBusy) {
                SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
                String endpoint = settings.getString("mcpUrl", "").trim();
                String token = settings.getString("mcpToken", "").trim();
                if (!endpoint.isEmpty()) {
                    String bridgeJob = McpBridgeStore.getActiveJobId(EditorActivity.this);
                    boolean serviceOwnsJob = mcpJobId != null
                            && !mcpJobId.isEmpty()
                            && mcpJobId.equals(bridgeJob);

                    if (mcpJobId == null || mcpJobId.isEmpty()) {
                        autoQueueOrFetchChatGpt();
                    } else if (!serviceOwnsJob) {
                        fetchChatGptResult(endpoint, token, false);
                    }
                }
            }

            boolean keepBridge = mcpForeground
                    || SystemClock.elapsedRealtime() < mcpBackgroundUntilElapsed;
            if (!destroyed && keepBridge) {
                mcpHandler.postDelayed(this, MCP_POLL_INTERVAL_MS);
            }
        }
    };

    private final Object rendererLock = new Object();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private volatile boolean destroyed;
    private volatile boolean exportBusy;
    private volatile boolean detectFieldsOnNextRender;

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

    private static final class PositionStripper extends PDFTextStripper {
        private final JSONArray blocks = new JSONArray();
        private final int pageIndex;
        private final float pageWidth;
        private final float pageHeight;

        PositionStripper(int pageIndex, float pageWidth, float pageHeight) throws IOException {
            this.pageIndex = pageIndex;
            this.pageWidth = Math.max(1f, pageWidth);
            this.pageHeight = Math.max(1f, pageHeight);
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            if (blocks.length() >= MAX_TEXT_BLOCKS_PER_PAGE
                    || positions == null || positions.isEmpty()) return;

            String clean = text == null ? "" : text.replaceAll("\\s+", " ").trim();
            if (clean.isEmpty()) return;
            if (clean.length() > 700) clean = clean.substring(0, 700);

            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = 0f;
            float maxY = 0f;
            float fontSize = 0f;

            for (TextPosition position : positions) {
                if (position == null) continue;
                float x = position.getXDirAdj();
                float y = position.getYDirAdj();
                float w = Math.max(0f, position.getWidthDirAdj());
                float h = Math.max(0f, position.getHeightDir());
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + w);
                maxY = Math.max(maxY, y + h);
                fontSize = Math.max(fontSize, position.getFontSizeInPt());
            }
            if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) return;

            try {
                JSONObject block = new JSONObject();
                block.put("page_index", pageIndex);
                block.put("text", clean);
                block.put("x", clamp01(minX / pageWidth));
                block.put("y", clamp01(minY / pageHeight));
                block.put("width", clamp01((maxX - minX) / pageWidth));
                block.put("height", clamp01((maxY - minY) / pageHeight));
                block.put("font_size", Math.max(1f, fontSize));
                blocks.put(block);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        PDFBoxResourceLoader.init(getApplicationContext());

        pdfView = findViewById(R.id.pdfView);
        tvPage = findViewById(R.id.tvPage);
        tvPosition = findViewById(R.id.tvPosition);
        tvFieldStatus = findViewById(R.id.tvFieldStatus);
        tvTextSize = findViewById(R.id.tvTextSize);
        tvChatGptStatus = findViewById(R.id.tvChatGptStatus);
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
        findViewById(R.id.btnMcpSync).setOnClickListener(v -> manualMcpSync());
        findViewById(R.id.btnMcpLogs).setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));

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
        } else {
            Uri inboundUri = getIntent() == null ? null : getIntent().getData();
            String inboundJob = getIntent() == null
                    ? "" : getIntent().getStringExtra(EXTRA_MCP_JOB_ID);
            String inboundName = getIntent() == null
                    ? "" : getIntent().getStringExtra(EXTRA_MCP_DOCUMENT_NAME);

            if (inboundUri != null && inboundJob != null && !inboundJob.trim().isEmpty()) {
                pendingInboundJobId = inboundJob.trim();
                currentDocumentNameOverride = inboundName == null ? "" : inboundName.trim();
                openPdf(inboundUri, null, -1);
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
        if (sourceUri != null && mcpJobId != null && !mcpJobId.isEmpty()) {
            McpBridgeStore.clearActiveJob(this, mcpJobId);
            stopService(new Intent(this, McpBridgeService.class));
            deactivateCurrentMcpDocument();
        }
        closePdf();
        try {
            if ((pendingInboundJobId == null || pendingInboundJobId.isEmpty()) && data != null) {
                currentDocumentNameOverride = "";
            }
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

            if (pendingInboundJobId != null && !pendingInboundJobId.isEmpty()) {
                mcpJobId = pendingInboundJobId;
                pendingInboundJobId = "";
                inboundNeedsContextSync = true;
                persistMcpJob();
            } else {
                // Reopening the same PDF keeps its MCP link so commands survive updates.
                loadMcpJobForDraft();
            }
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
            if (inboundNeedsContextSync) {
                syncInboundDocumentContext();
            } else if (mcpJobId != null && !mcpJobId.isEmpty()) {
                ensurePersistentMcpBridge();
            } else {
                autoQueueOrFetchChatGpt();
            }
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
        final boolean shouldDetectFields = detectFieldsOnNextRender;
        detectFieldsOnNextRender = false;
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
                    fields = new ArrayList<>();
                }

                if (shouldDetectFields) {
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
                int finalPageWidth = rendered.pageWidth;
                int finalPageHeight = rendered.pageHeight;
                List<FormField> finalFields = fields;
                bitmap = null; // Ownership moves to PdfOverlayView on the UI thread.
                runOnUiThread(() -> {
                    if (destroyed || generation != renderGeneration.get() || requestedPage != pageIndex) {
                        recycle(finalBitmap);
                        return;
                    }
                    pdfView.setPage(finalBitmap, finalPageWidth, finalPageHeight,
                            currentPageOverlays());
                    pdfView.setDetectedFields(finalFields);
                    int count = getPageCountSafe();
                    tvPage.setText("Page " + (requestedPage + 1) + " / " + count);
                    updateFieldStatus(finalFields.size());
                    btnDetectFields.setEnabled(true);

                    selectedX = 0.10f;
                    selectedY = 0.10f;
                    if (finalFields.isEmpty()) {
                        tvPosition.setText("Placement libre • ChatGPT utilise l’image réelle de la page.");
                    } else {
                        tvPosition.setText(finalFields.size()
                                + " repère(s) optionnel(s) affiché(s) • aucun recalage automatique.");
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
        detectFieldsOnNextRender = true;
        tvFieldStatus.setText("Analyse optionnelle…");
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

    private void autoQueueOrFetchChatGpt() {
        if (getPageCountSafe() <= 0 || sourceUri == null || mcpBusy) return;

        SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "").trim();
        String token = settings.getString("mcpToken", "").trim();

        if (endpoint.isEmpty()) {
            tvPosition.setText("Document chargé. Configurez le MCP dans Réglages pour le rendre disponible dans ChatGPT.");
            return;
        }

        if (mcpJobId == null || mcpJobId.isEmpty()) {
            sendDocumentToChatGpt(endpoint, token);
        } else {
            ensurePersistentMcpBridge();
        }
    }

    private void syncInboundDocumentContext() {
        if (sourceUri == null || mcpJobId == null || mcpJobId.isEmpty() || mcpBusy) return;

        SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "").trim();
        String token = settings.getString("mcpToken", "").trim();
        if (endpoint.isEmpty()) {
            inboundNeedsContextSync = false;
            tvPosition.setText("PDF reçu de ChatGPT. Configurez le MCP dans Réglages.");
            return;
        }

        mcpBusy = true;
        tvPosition.setText("Analyse du PDF reçu de ChatGPT…");
        final Uri uriSnapshot = sourceUri;
        final int pageCount = getPageCountSafe();
        final String jobId = mcpJobId;

        worker.execute(() -> {
            try {
                JSONObject document = buildMcpDocumentContext(uriSnapshot, pageCount);
                JSONObject profile = buildMcpProfile();
                JSONArray fieldHints = buildMcpFieldHints(pageCount, document);

                McpBridgeStore.attachJob(
                        EditorActivity.this,
                        jobId,
                        uriSnapshot,
                        document.optString("name", "document.pdf"),
                        new ArrayList<>(overlays));
                startPersistentMcpBridgeService();

                int uploadedPages = uploadMcpPageImages(endpoint, token, jobId, pageCount);
                document.put("page_images_count", uploadedPages);
                document.put("page_images_ready", uploadedPages == pageCount);

                McpClient.updateJobContext(endpoint, token, jobId,
                        document, profile, fieldHints,
                        (success, message) -> runOnUiThread(() -> {
                            mcpBusy = false;
                            inboundNeedsContextSync = false;
                            if (destroyed || isFinishing() || !jobId.equals(mcpJobId)) return;

                            if (success) {
                                tvPosition.setText(uploadedPages == pageCount
                                        ? "PDF reçu • pages visibles par ChatGPT."
                                        : "PDF reçu • " + uploadedPages + "/" + pageCount
                                        + " page(s) visuelle(s) synchronisée(s).");
                                startPersistentMcpBridgeService();
                            } else {
                                tvPosition.setText(message);
                            }
                        }));
            } catch (Exception e) {
                AppLog.write(EditorActivity.this, "syncInboundContext", e);
                runOnUiThread(() -> {
                    mcpBusy = false;
                    inboundNeedsContextSync = false;
                    if (!destroyed && !isFinishing()) {
                        tvPosition.setText("Analyse du PDF reçu impossible : " + safeMessage(e));
                    }
                });
            }
        });
    }

    private void sendDocumentToChatGpt(String endpoint, String token) {
        // Démarre le pont pendant que l'activité est encore au premier plan.
        // Le service attend ensuite la création du job et continue même si
        // l'utilisateur revient immédiatement dans ChatGPT.
        startPersistentMcpBridgeService();
        mcpBusy = true;
        tvPosition.setText("Préparation automatique du document pour ChatGPT…");

        final Uri uriSnapshot = sourceUri;
        final int pageCount = getPageCountSafe();

        worker.execute(() -> {
            try {
                JSONObject document = buildMcpDocumentContext(uriSnapshot, pageCount);
                JSONObject profile = buildMcpProfile();
                JSONArray fieldHints = buildMcpFieldHints(pageCount, document);

                runOnUiThread(() -> tvPosition.setText("Envoi sécurisé vers ChatGPT…"));

                McpClient.createJob(endpoint, token, document, profile, fieldHints,
                        new McpClient.JobCallback() {
                            @Override
                            public void onCreated(String jobId, String status) {
                                runOnUiThread(() -> {
                                    if (destroyed || isFinishing()) return;
                                    mcpJobId = jobId;
                                    persistMcpJob();
                                    tvPosition.setText("Document envoyé • préparation des pages visuelles pour ChatGPT…");
                                });

                                worker.execute(() -> {
                                    try {
                                        McpBridgeStore.attachJob(
                                                EditorActivity.this,
                                                jobId,
                                                uriSnapshot,
                                                document.optString("name", "document.pdf"),
                                                new ArrayList<>(overlays));
                                        startPersistentMcpBridgeService();

                                        int uploadedPages = uploadMcpPageImages(
                                                endpoint, token, jobId, pageCount);
                                        document.put("page_images_count", uploadedPages);
                                        document.put("page_images_ready", uploadedPages == pageCount);

                                        McpClient.updateJobContext(endpoint, token, jobId,
                                                document, profile, fieldHints,
                                                (success, message) -> runOnUiThread(() -> {
                                                    mcpBusy = false;
                                                    if (destroyed || isFinishing()
                                                            || !jobId.equals(mcpJobId)) return;

                                                    if (success) {
                                                        tvPosition.setText(uploadedPages == pageCount
                                                                ? "Document prêt • ChatGPT peut voir toutes les pages."
                                                                : "Document prêt • " + uploadedPages + "/"
                                                                + pageCount + " page(s) visuelle(s) disponibles.");
                                                        Toast.makeText(EditorActivity.this,
                                                                "Document disponible dans ChatGPT",
                                                                Toast.LENGTH_LONG).show();
                                                    } else {
                                                        tvPosition.setText(message);
                                                    }
                                                }));
                                    } catch (Exception e) {
                                        AppLog.write(EditorActivity.this,
                                                "uploadMcpPageImages", e);
                                        runOnUiThread(() -> {
                                            mcpBusy = false;
                                            if (!destroyed && !isFinishing()) {
                                                tvPosition.setText("Document envoyé, mais pages visuelles incomplètes : "
                                                        + safeMessage(e));
                                            }
                                        });
                                    }
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> finishMcpError(message));
                            }
                        });
            } catch (Exception e) {
                AppLog.write(this, "prepareMcpDocument", e);
                runOnUiThread(() -> finishMcpError(
                        "Préparation ChatGPT impossible : " + safeMessage(e)));
            }
        });
    }

    private void ensurePersistentMcpBridge() {
        if (sourceUri == null || mcpJobId == null || mcpJobId.isEmpty()) return;

        final Uri uriSnapshot = sourceUri;
        final String jobId = mcpJobId;
        final String name = currentDocumentNameOverride == null
                || currentDocumentNameOverride.trim().isEmpty()
                ? "document.pdf" : currentDocumentNameOverride.trim();
        final List<TextOverlay> overlaySnapshot = new ArrayList<>(overlays);

        worker.execute(() -> {
            try {
                McpBridgeStore.attachJob(
                        EditorActivity.this,
                        jobId,
                        uriSnapshot,
                        name,
                        overlaySnapshot);
                startPersistentMcpBridgeService();
            } catch (Exception e) {
                AppLog.write(EditorActivity.this, "ensurePersistentMcpBridge", e);
            }
        });
    }

    private void manualMcpSync() {
        if (sourceUri == null || getPageCountSafe() <= 0) {
            tvChatGptStatus.setText("ChatGPT : choisissez d'abord un PDF");
            Toast.makeText(this, "Choisissez d’abord un PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "").trim();
        if (endpoint.isEmpty()) {
            tvChatGptStatus.setText("ChatGPT : MCP non configuré");
            Toast.makeText(this, "Configurez le MCP dans Réglages", Toast.LENGTH_LONG).show();
            return;
        }

        AppLog.write(this, "MCP_SYNC bouton manuel", null);
        tvChatGptStatus.setText("ChatGPT : reconnexion et synchronisation…");

        if (mcpJobId == null || mcpJobId.isEmpty()) {
            autoQueueOrFetchChatGpt();
            return;
        }

        File bridgeSource = McpBridgeStore.getSourceFile(this, mcpJobId);
        if (bridgeSource == null || !bridgeSource.isFile()) {
            ensurePersistentMcpBridge();
            return;
        }

        stopService(new Intent(this, McpBridgeService.class));
        McpBridgeState.setRunning(this, false);
        mcpHandler.postDelayed(this::startPersistentMcpBridgeService, 250L);
    }

    private void refreshMcpStatusUi() {
        if (tvChatGptStatus == null) return;
        String text = McpBridgeState.oneLine(this);
        tvChatGptStatus.setText(text);

        McpBridgeState.Snapshot snapshot = McpBridgeState.read(this);
        if (snapshot.connected) {
            tvChatGptStatus.setTextColor(Color.rgb(219, 255, 221));
        } else if (snapshot.running) {
            tvChatGptStatus.setTextColor(Color.rgb(255, 225, 168));
        } else {
            tvChatGptStatus.setTextColor(Color.rgb(255, 181, 138));
        }
    }

    private void startPersistentMcpBridgeService() {
        try {
            Intent serviceIntent = new Intent(this, McpBridgeService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            AppLog.write(this, "startPersistentMcpBridgeService", e);
        }
    }

    private int uploadMcpPageImages(String endpoint, String token,
                                    String jobId, int pageCount) {
        int uploaded = 0;

        for (int i = 0; i < pageCount; i++) {
            Bitmap renderedBitmap = null;
            Bitmap uploadBitmap = null;
            try {
                RenderedPage rendered = renderPage(i, false);
                renderedBitmap = rendered.bitmap;
                uploadBitmap = scaleForMcpVision(renderedBitmap, 1600);

                byte[] jpeg = encodeMcpJpeg(uploadBitmap);
                McpClient.uploadPageImageBlocking(
                        endpoint, token, jobId, i, jpeg);
                uploaded++;

                final int progress = uploaded;
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing() && jobId.equals(mcpJobId)) {
                        tvPosition.setText("Analyse visuelle ChatGPT • "
                                + progress + "/" + pageCount + " page(s)");
                    }
                });
            } catch (Exception e) {
                AppLog.write(this, "uploadMcpPageImage page=" + i, e);
            } finally {
                if (uploadBitmap != null && uploadBitmap != renderedBitmap) {
                    recycle(uploadBitmap);
                }
                recycle(renderedBitmap);
            }
        }

        return uploaded;
    }

    private Bitmap scaleForMcpVision(Bitmap source, int maxDimension) {
        if (source == null || source.isRecycled()) return source;
        int width = source.getWidth();
        int height = source.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxDimension) return source;

        float scale = maxDimension / (float) largest;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    private byte[] encodeMcpJpeg(Bitmap bitmap) throws IOException {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IOException("Page visuelle indisponible");
        }

        int[] qualities = new int[]{82, 72, 62, 52, 44};
        for (int quality : qualities) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                continue;
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length >= 100 && bytes.length <= 1_450_000) {
                return bytes;
            }
        }

        throw new IOException("Page visuelle trop volumineuse");
    }

    private void fetchChatGptResult(String endpoint, String token, boolean userRequested) {
        if (mcpJobId == null || mcpJobId.isEmpty() || mcpBusy) return;
        mcpBusy = true;
        if (userRequested) tvPosition.setText("Synchronisation avec ChatGPT…");

        final String requestedJob = mcpJobId;
        McpClient.getJob(endpoint, token, requestedJob, new McpClient.JobStatusCallback() {
            @Override
            public void onStatus(String status, JSONObject fillPlan, String errorMessage) {
                runOnUiThread(() -> {
                    mcpBusy = false;
                    if (destroyed || isFinishing() || !requestedJob.equals(mcpJobId)) return;

                    if ("ready".equalsIgnoreCase(status) && fillPlan != null) {
                        try {
                            String commandId = fillPlan.optString("command_id", "").trim();
                            if (commandId.isEmpty()) {
                                commandId = "legacy-" + Integer.toHexString(fillPlan.toString().hashCode());
                            }

                            String lastApplied = getLastAppliedMcpCommand();
                            int changed = 0;
                            if (!commandId.equals(lastApplied)) {
                                changed = applyMcpPlan(fillPlan);
                                saveDraft(true);
                                rememberAppliedMcpCommand(commandId);
                                renderCurrentPage();
                            }

                            tvPosition.setText("Commande ChatGPT appliquée • "
                                    + changed + " nouvel(aux) élément(s). Confirmation au serveur…");
                            acknowledgeMcpApplied(endpoint, token, requestedJob, commandId);
                        } catch (Exception e) {
                            AppLog.write(EditorActivity.this, "applyMcpPlan", e);
                            finishMcpError("Commande ChatGPT invalide : " + safeMessage(e));
                        }
                        return;
                    }

                    if ("failed".equalsIgnoreCase(status)
                            || "cancelled".equalsIgnoreCase(status)) {
                        clearPersistedMcpJob();
                        tvPosition.setText(errorMessage == null || errorMessage.trim().isEmpty()
                                ? "La commande ChatGPT n’a pas été appliquée."
                                : errorMessage);
                        autoQueueOrFetchChatGpt();
                        return;
                    }

                    tvPosition.setText("Document disponible dans ChatGPT.");
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
                    boolean staleJob = lower.contains("404")
                            || lower.contains("introuvable")
                            || lower.contains("expir");

                    if (staleJob) {
                        mcpBusy = false;
                        clearPersistedMcpJob();
                        if (!destroyed && !isFinishing() && sourceUri != null) {
                            tvPosition.setText("Ancien lien MCP supprimé • resynchronisation du PDF…");
                            autoQueueOrFetchChatGpt();
                        }
                        return;
                    }

                    if (userRequested) {
                        finishMcpError(message);
                    } else {
                        mcpBusy = false;
                        if (!destroyed && !isFinishing()) {
                            tvPosition.setText("Synchronisation ChatGPT en attente…");
                        }
                    }
                });
            }
        });
    }

    private void acknowledgeMcpApplied(String endpoint, String token, String jobId,
                                       String commandId) throws Exception {
        final JSONArray current = buildMcpCurrentOverlays();
        final JSONObject profile = buildMcpProfile();
        final int currentPage = pageIndex;
        final float textSize = currentTextSize;

        mcpBusy = true;
        McpClient.acknowledgeApplied(endpoint, token, jobId, commandId,
                current, profile, currentPage, textSize,
                (success, message) -> runOnUiThread(() -> {
                    mcpBusy = false;
                    if (destroyed || isFinishing() || !jobId.equals(mcpJobId)) return;

                    if (success) {
                        tvPosition.setText("Document rempli • création de la prévisualisation pour ChatGPT…");
                        uploadPreviewThenFilledPdf(endpoint, token, jobId);
                    } else {
                        tvPosition.setText("Document rempli localement • confirmation MCP à réessayer.");
                    }
                }));
    }

    private void uploadPreviewThenFilledPdf(String endpoint, String token, String jobId) {
        if (sourceUri == null || jobId == null || jobId.isEmpty()) return;

        mcpBusy = true;
        final List<TextOverlay> overlaySnapshot = new ArrayList<>(overlays);
        final int fallbackPage = pageIndex;

        worker.execute(() -> {
            int previews = 0;
            try {
                previews = uploadMcpPreviewImages(
                        endpoint, token, jobId, overlaySnapshot, fallbackPage);
            } catch (Exception e) {
                AppLog.write(EditorActivity.this, "uploadMcpPreviewImages", e);
            }

            final int previewCount = previews;
            runOnUiThread(() -> {
                mcpBusy = false;
                if (destroyed || isFinishing() || !jobId.equals(mcpJobId)) return;

                if (previewCount > 0) {
                    tvPosition.setText("Prévisualisation envoyée à ChatGPT • contrôle visuel possible.");
                } else {
                    tvPosition.setText("Document rempli • prévisualisation non disponible, envoi du PDF final…");
                }
                uploadFilledPdfToMcp(endpoint, token, jobId);
            });
        });
    }

    private int uploadMcpPreviewImages(String endpoint, String token, String jobId,
                                       List<TextOverlay> overlaySnapshot,
                                       int fallbackPage) {
        Set<Integer> pages = new HashSet<>();
        for (TextOverlay overlay : overlaySnapshot) {
            if (overlay != null && overlay.pageIndex >= 0
                    && overlay.pageIndex < getPageCountSafe()) {
                pages.add(overlay.pageIndex);
            }
        }
        if (pages.isEmpty() && fallbackPage >= 0 && fallbackPage < getPageCountSafe()) {
            pages.add(fallbackPage);
        }

        int uploaded = 0;
        for (Integer page : pages) {
            Bitmap renderedBitmap = null;
            Bitmap uploadBitmap = null;
            try {
                RenderedPage rendered = renderPage(page, false);
                renderedBitmap = rendered.bitmap;

                Canvas canvas = new Canvas(renderedBitmap);
                Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setColor(Color.BLACK);
                float renderScale = renderedBitmap.getWidth()
                        / (float) Math.max(1, rendered.pageWidth);

                for (TextOverlay overlay : overlaySnapshot) {
                    if (overlay == null || overlay.pageIndex != page) continue;
                    drawOverlayExact(canvas, textPaint, overlay,
                            renderedBitmap.getWidth(), renderedBitmap.getHeight(),
                            renderScale);
                }

                uploadBitmap = scaleForMcpVision(renderedBitmap, 1600);
                byte[] jpeg = encodeMcpJpeg(uploadBitmap);
                McpClient.uploadPreviewImageBlocking(
                        endpoint, token, jobId, page, jpeg);
                uploaded++;
            } catch (Exception e) {
                AppLog.write(this, "uploadMcpPreview page=" + page, e);
            } finally {
                if (uploadBitmap != null && uploadBitmap != renderedBitmap) {
                    recycle(uploadBitmap);
                }
                recycle(renderedBitmap);
            }
        }
        return uploaded;
    }

    private void uploadFilledPdfToMcp(String endpoint, String token, String jobId) {
        if (sourceUri == null || jobId == null || jobId.isEmpty()) return;

        mcpBusy = true;
        final List<TextOverlay> overlaySnapshot = new ArrayList<>(overlays);

        worker.execute(() -> {
            File target = new File(getCacheDir(), "mcp-filled-" + jobId + ".pdf");
            try {
                writeFilledPdfFile(target, overlaySnapshot);
                McpClient.uploadFilledPdf(endpoint, token, jobId, target,
                        (success, message) -> runOnUiThread(() -> {
                            mcpBusy = false;
                            target.delete();
                            if (destroyed || isFinishing() || !jobId.equals(mcpJobId)) return;

                            if (success) {
                                tvPosition.setText("PDF final disponible dans ChatGPT.");
                            } else {
                                tvPosition.setText("Document rempli • " + message);
                            }
                        }));
            } catch (Exception e) {
                AppLog.write(EditorActivity.this, "uploadFilledPdf", e);
                target.delete();
                runOnUiThread(() -> {
                    mcpBusy = false;
                    if (!destroyed && !isFinishing()) {
                        tvPosition.setText("Document rempli • PDF final non envoyé : " + safeMessage(e));
                    }
                });
            }
        });
    }

    private void drawOverlayExact(Canvas canvas, Paint textPaint, TextOverlay overlay,
                                  int pageWidth, int pageHeight, float textScale) {
        if (overlay == null || overlay.text == null || overlay.text.isEmpty()) return;

        float safeScale = Math.max(0.0001f, textScale);
        textPaint.setTextSize(Math.max(1f, overlay.textSize * safeScale));
        textPaint.setTextAlign(toPaintAlign(overlay.align));

        float x = overlay.x * pageWidth;
        float y = overlay.y * pageHeight;

        if (overlay.isCheckbox()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            y = y - (fm.ascent + fm.descent) * 0.5f;
        }

        canvas.drawText(overlay.text, x, y, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private static Paint.Align toPaintAlign(String align) {
        if (TextOverlay.ALIGN_CENTER.equals(align)) return Paint.Align.CENTER;
        if (TextOverlay.ALIGN_RIGHT.equals(align)) return Paint.Align.RIGHT;
        return Paint.Align.LEFT;
    }

    private void writeFilledPdfFile(File target, List<TextOverlay> overlaySnapshot) throws Exception {
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
                            drawOverlayExact(canvas, textPaint, overlay,
                                    rendered.pageWidth, rendered.pageHeight, 1f);
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

            try (FileOutputStream stream = new FileOutputStream(target, false)) {
                out.writeTo(stream);
                stream.flush();
            }
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    private int applyMcpPlan(JSONObject fillPlan) throws Exception {
        String mode = fillPlan.optString("mode", "append").trim().toLowerCase(Locale.ROOT);
        int targetPage = fillPlan.optInt("target_page", -1);
        List<TextOverlay> incoming = AiFillPlan.parse(
                fillPlan.toString(), getPageCountSafe());

        int changed = 0;
        if ("replace_document".equals(mode) || "replace".equals(mode)) {
            changed += overlays.size();
            overlays.clear();
        } else if ("clear_document".equals(mode)) {
            changed += overlays.size();
            overlays.clear();
            incoming.clear();
        } else if ("replace_page".equals(mode) || "clear_page".equals(mode)) {
            if (targetPage < 0 || targetPage >= getPageCountSafe()) {
                throw new IllegalArgumentException("Page cible invalide");
            }
            for (int i = overlays.size() - 1; i >= 0; i--) {
                if (overlays.get(i).pageIndex == targetPage) {
                    overlays.remove(i);
                    changed++;
                }
            }
            if ("clear_page".equals(mode)) incoming.clear();
        } else if (!"append".equals(mode)
                && !"update_profile".equals(mode)
                && !"set_editor_state".equals(mode)) {
            throw new IllegalArgumentException("Mode MCP inconnu : " + mode);
        }

        if (!incoming.isEmpty()) {
            overlays.addAll(incoming);
            changed += incoming.size();
        }

        JSONObject profileUpdates = fillPlan.optJSONObject("profile_updates");
        if (profileUpdates != null) {
            changed += applyProfileUpdates(profileUpdates);
        }

        JSONObject editorUpdates = fillPlan.optJSONObject("editor_updates");
        if (editorUpdates != null) {
            if (editorUpdates.has("text_size")) {
                currentTextSize = Math.max(4f, Math.min(144f,
                        (float) editorUpdates.optDouble("text_size", currentTextSize)));
                updateTextSizeLabel();
                changed++;
            }
            if (editorUpdates.has("page_index")) {
                int requested = editorUpdates.optInt("page_index", pageIndex);
                int count = getPageCountSafe();
                if (count > 0) {
                    pageIndex = Math.max(0, Math.min(count - 1, requested));
                    changed++;
                }
            }
        }
        return changed;
    }

    private int applyProfileUpdates(JSONObject updates) {
        SharedPreferences.Editor editor = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE).edit();
        String[] keys = new String[]{
                "firstName", "lastName", "birthDate", "birthPlace",
                "address", "postalCode", "city", "phone", "email", "otherId"
        };
        int changed = 0;
        for (String key : keys) {
            if (!updates.has(key)) continue;
            String value = updates.optString(key, "");
            editor.putString(key, value);
            changed++;
        }
        if (changed > 0) editor.apply();
        return changed;
    }

    private JSONObject buildMcpDocumentContext(Uri uri, int expectedPageCount) throws Exception {
        JSONObject root = new JSONObject();
        String resolvedName = currentDocumentNameOverride == null
                ? "" : currentDocumentNameOverride.trim();
        if (resolvedName.isEmpty()) {
            resolvedName = uri == null || uri.getLastPathSegment() == null
                    ? "document.pdf" : uri.getLastPathSegment();
        }
        root.put("name", resolvedName);
        root.put("page_count", expectedPageCount);
        root.put("app_version", BuildConfig.VERSION_NAME);
        root.put("app_version_code", BuildConfig.VERSION_CODE);
        root.put("current_page_index", pageIndex);
        root.put("current_text_size", currentTextSize);
        root.put("current_overlays", buildMcpCurrentOverlays());
        root.put("capabilities", AiFillPlan.capabilities());

        JSONArray pages = new JSONArray();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("PDF inaccessible");
            try (PDDocument document = PDDocument.load(input)) {
                int count = Math.min(expectedPageCount, document.getNumberOfPages());
                for (int i = 0; i < count; i++) {
                    PDPage page = document.getPage(i);
                    float width = Math.max(1f, page.getMediaBox().getWidth());
                    float height = Math.max(1f, page.getMediaBox().getHeight());

                    PositionStripper stripper = new PositionStripper(i, width, height);
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String text = stripper.getText(document);
                    if (text == null) text = "";
                    text = text.trim();
                    if (text.length() > MAX_TEXT_PER_PAGE) {
                        text = text.substring(0, MAX_TEXT_PER_PAGE);
                    }

                    JSONObject pageJson = new JSONObject();
                    pageJson.put("page_index", i);
                    pageJson.put("page_number", i + 1);
                    pageJson.put("width", width);
                    pageJson.put("height", height);
                    pageJson.put("unit", "PDF page unit");
                    pageJson.put("plain_text", text);
                    pageJson.put("text_blocks", stripper.blocks);
                    pages.put(pageJson);
                }
            }
        }
        root.put("pages", pages);
        root.put("instruction",
                "ChatGPT décide du placement final après inspection visuelle de la vraie page. "
                        + "Les x/y, la taille, l'alignement et le kind renvoyés sont autoritaires. "
                        + "L'application ne snap pas vers les champs détectés, ne corrige pas x/y et ne redimensionne pas le texte. "
                        + "Pour kind=text : x est l'ancre d'alignement et y la baseline exacte. "
                        + "Pour kind=checkbox : x/y sont le centre exact de la marque. "
                        + "Les field_hints sont uniquement des indices facultatifs.");
        return root;
    }

    private JSONArray buildMcpCurrentOverlays() throws Exception {
        JSONArray items = new JSONArray();
        for (TextOverlay overlay : overlays) {
            JSONObject item = new JSONObject();
            item.put("page_index", overlay.pageIndex);
            item.put("x", overlay.x);
            item.put("y", overlay.y);
            item.put("text", overlay.text);
            item.put("size", overlay.textSize);
            item.put("align", overlay.align);
            item.put("kind", overlay.kind);
            if (overlay.width > 0f) item.put("width", overlay.width);
            if (overlay.height > 0f) item.put("height", overlay.height);
            items.put(item);
        }
        return items;
    }

    private JSONObject buildMcpProfile() throws Exception {
        SharedPreferences p = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        JSONObject profile = new JSONObject();
        String[] keys = new String[]{
                "firstName", "lastName", "birthDate", "birthPlace",
                "address", "postalCode", "city", "phone", "email", "otherId"
        };
        for (String key : keys) {
            String value = p.getString(key, "");
            if (value != null && !value.trim().isEmpty()) {
                profile.put(key, value.trim());
            }
        }
        return profile;
    }

    private JSONArray buildMcpFieldHints(int pageCount, JSONObject document) {
        // Architecture visuelle v3: aucune détection géométrique ne décide pour ChatGPT.
        // Les pages propres + le texte/OCR disponible sont la source de placement.
        return new JSONArray();
    }

    private JSONArray findNearbyText(FormField field, JSONArray blocks) throws Exception {
        JSONArray result = new JSONArray();
        if (blocks == null || blocks.length() == 0) return result;

        final int maxCandidates = 4;
        List<JSONObject> candidates = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        float fx = field.centerX();
        float fy = field.centerY();

        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) continue;
            String text = block.optString("text", "").replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            if (text.length() > 240) text = text.substring(0, 240);

            float bx = (float) block.optDouble("x", 0.0);
            float by = (float) block.optDouble("y", 0.0);
            float bw = (float) block.optDouble("width", 0.0);
            float bh = (float) block.optDouble("height", 0.0);
            float bcx = bx + bw * 0.5f;
            float bcy = by + bh * 0.5f;

            float dx = Math.abs(bcx - fx);
            float dy = Math.abs(bcy - fy);
            boolean sameLine = dy <= Math.max(0.025f, field.height * 2.5f);
            boolean nearbyAbove = by <= field.y && field.y - (by + bh) <= 0.055f
                    && horizontalOverlap(field.x, field.width, bx, bw) > 0.08f;

            float score;
            if (field.type == FormField.Type.CHECKBOX) {
                boolean rightLabel = bx >= field.x - 0.01f && bx <= field.x + 0.30f && sameLine;
                boolean leftLabel = bx + bw <= field.x + 0.015f
                        && field.x - (bx + bw) <= 0.22f && sameLine;
                if (!rightLabel && !leftLabel && !nearbyAbove) continue;
                score = dy * 4f + dx;
                if (rightLabel) score -= 0.08f;
            } else {
                boolean leftLabel = bx + bw <= field.x + 0.02f
                        && field.x - (bx + bw) <= 0.25f && sameLine;
                if (!leftLabel && !nearbyAbove && !(sameLine && dx <= 0.20f)) continue;
                score = dy * 3f + dx;
                if (leftLabel) score -= 0.05f;
                if (nearbyAbove) score -= 0.03f;
            }

            int pos = 0;
            while (pos < scores.size() && scores.get(pos) <= score) pos++;
            scores.add(pos, score);
            candidates.add(pos, new JSONObject().put("text", text)
                    .put("x", bx).put("y", by).put("width", bw).put("height", bh));
            if (candidates.size() > maxCandidates) {
                candidates.remove(candidates.size() - 1);
                scores.remove(scores.size() - 1);
            }
        }

        for (JSONObject candidate : candidates) result.put(candidate);
        return result;
    }

    private static float horizontalOverlap(float ax, float aw, float bx, float bw) {
        float left = Math.max(ax, bx);
        float right = Math.min(ax + aw, bx + bw);
        if (right <= left) return 0f;
        return (right - left) / Math.max(0.0001f, Math.min(aw, bw));
    }

    private void loadMcpJobForDraft() {
        if (draftKey == null) {
            mcpJobId = "";
            return;
        }

        SharedPreferences prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE);
        // Preserve a still-valid pending/ready command across an in-place app update.
        // The server validates expiry/session ownership; a stale/cancelled job is replaced
        // automatically after the first synchronization attempt.
        mcpJobId = prefs.getString(draftKey + MCP_JOB_SUFFIX, "");
        if (mcpJobId == null) mcpJobId = "";
    }

    private void persistMcpJob() {
        if (draftKey == null || mcpJobId == null || mcpJobId.isEmpty()) return;
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
                .edit()
                .putString(draftKey + MCP_JOB_SUFFIX, mcpJobId)
                .putString(draftKey + MCP_JOB_VERSION_SUFFIX, BuildConfig.VERSION_NAME)
                .remove(draftKey + MCP_COMMAND_SUFFIX)
                .apply();
    }

    private String getLastAppliedMcpCommand() {
        if (draftKey == null) return "";
        return getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
                .getString(draftKey + MCP_COMMAND_SUFFIX, "");
    }

    private void rememberAppliedMcpCommand(String commandId) {
        if (draftKey == null || commandId == null || commandId.isEmpty()) return;
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
                .edit()
                .putString(draftKey + MCP_COMMAND_SUFFIX, commandId)
                .apply();
    }

    private void clearPersistedMcpJob() {
        if (draftKey != null) {
            getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
                    .edit()
                    .remove(draftKey + MCP_JOB_SUFFIX)
                    .remove(draftKey + MCP_JOB_VERSION_SUFFIX)
                    .remove(draftKey + MCP_COMMAND_SUFFIX)
                    .apply();
        }
        mcpJobId = "";
    }

    private void finishMcpError(String message) {
        mcpBusy = false;
        tvPosition.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
                        Math.max(4f, Math.min(144f, (float) item.optDouble("size", 8.0))),
                        TextOverlay.normalizeAlign(item.optString("align", TextOverlay.ALIGN_LEFT)),
                        TextOverlay.normalizeKind(item.optString("kind", TextOverlay.KIND_TEXT)),
                        clamp01((float) item.optDouble("width", 0.0)),
                        clamp01((float) item.optDouble("height", 0.0))
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
                item.put("align", overlay.align);
                item.put("kind", overlay.kind);
                if (overlay.width > 0f) item.put("width", overlay.width);
                if (overlay.height > 0f) item.put("height", overlay.height);
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

            if (mcpJobId != null && !mcpJobId.isEmpty()
                    && mcpJobId.equals(McpBridgeStore.getActiveJobId(this))) {
                McpBridgeStore.saveOverlays(this, mcpJobId, new ArrayList<>(overlays));
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
                            drawOverlayExact(canvas, textPaint, overlay,
                                    rendered.pageWidth, rendered.pageHeight, 1f);
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
                    drawOverlayExact(canvas, textPaint, overlay,
                            bitmap.getWidth(), bitmap.getHeight(), textScale);
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
    protected void onStart() {
        super.onStart();
        if (!bridgeReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    mcpBridgeReceiver,
                    new IntentFilter(McpBridgeService.ACTION_UPDATED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            bridgeReceiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mcpForeground = true;
        mcpBackgroundUntilElapsed = 0L;

        if (mcpJobId != null && !mcpJobId.isEmpty()
                && mcpJobId.equals(McpBridgeStore.getActiveJobId(this))) {
            startPersistentMcpBridgeService();
            List<TextOverlay> bridgeOverlays =
                    McpBridgeStore.loadOverlays(this, mcpJobId);
            overlays.clear();
            overlays.addAll(bridgeOverlays);
            renderCurrentPage();
        }

        mcpHandler.removeCallbacks(mcpPollRunnable);
        mcpHandler.removeCallbacks(mcpStatusUiRunnable);
        mcpHandler.post(mcpPollRunnable);
        mcpHandler.post(mcpStatusUiRunnable);
    }

    @Override
    protected void onStop() {
        if (bridgeReceiverRegistered) {
            try {
                unregisterReceiver(mcpBridgeReceiver);
            } catch (Exception ignored) {
            }
            bridgeReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        mcpForeground = false;
        if (sourceUri != null && mcpJobId != null && !mcpJobId.isEmpty()) {
            mcpBackgroundUntilElapsed = SystemClock.elapsedRealtime()
                    + MCP_BACKGROUND_BRIDGE_MS;
            mcpHandler.removeCallbacks(mcpPollRunnable);
            mcpHandler.post(mcpPollRunnable);
        } else {
            mcpBackgroundUntilElapsed = 0L;
            mcpHandler.removeCallbacks(mcpPollRunnable);
        }
        mcpHandler.removeCallbacks(mcpStatusUiRunnable);
        if (draftKey != null) saveDraft(true);
        super.onPause();
    }

    private void deactivateCurrentMcpDocument() {
        final String jobId = mcpJobId == null ? "" : mcpJobId.trim();
        if (jobId.isEmpty()) return;

        SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "").trim();
        String token = settings.getString("mcpToken", "").trim();
        if (endpoint.isEmpty()) return;

        McpClient.deactivateJob(endpoint, token, jobId, (success, message) -> {
            if (!success) {
                AppLog.write(EditorActivity.this,
                        "deactivateMcpJob " + jobId + " : " + message,
                        null);
            }
        });
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
        mcpJobId = "";
        mcpBusy = false;

        synchronized (detectedFieldsByPage) {
            detectedFieldsByPage.clear();
        }
    }

    @Override
    protected void onDestroy() {
        // Le document MCP reste disponible même si l'utilisateur quitte l'éditeur
        // pour revenir dans ChatGPT. Il n'est désactivé que lorsqu'un autre PDF
        // remplace explicitement le document courant.
        destroyed = true;
        renderGeneration.incrementAndGet();
        if (draftKey != null) saveDraft(true);
        mcpForeground = false;
        mcpHandler.removeCallbacksAndMessages(null);
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
