package com.chasmet.remplissagepapierofficiel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class McpBridgeService extends Service {
    public static final String ACTION_UPDATED =
            "com.chasmet.remplissagepapierofficiel.MCP_BRIDGE_UPDATED";
    public static final String EXTRA_JOB_ID = "job_id";

    private static final String CHANNEL_ID = "chatgpt_bridge";
    private static final int NOTIFICATION_ID = 1701;
    private static final String SETTINGS_PREFS = "settings";
    private static final long POLL_SECONDS = 1L;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean stopped;
    private long noDocumentSinceElapsed;

    @Override
    public void onCreate() {
        super.onCreate();
        McpBridgeState.setRunning(this, true);
        AppLog.write(this, "MCP_BRIDGE service démarré", null);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Connexion à ChatGPT en cours…"));
        scheduler.scheduleWithFixedDelay(this::pollSafely,
                0L, POLL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        McpBridgeState.setRunning(this, true);
        startForeground(NOTIFICATION_ID, buildNotification("ChatGPT peut travailler sur le document"));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopped = true;
        McpBridgeState.setRunning(this, false);
        AppLog.write(this, "MCP_BRIDGE service arrêté", null);
        scheduler.shutdownNow();
        busy.set(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void pollSafely() {
        if (stopped || !busy.compareAndSet(false, true)) return;

        String jobId = McpBridgeStore.getActiveJobId(this);
        File source = McpBridgeStore.getSourceFile(this, jobId);
        if (jobId == null || jobId.isEmpty() || source == null || !source.isFile()) {
            if (noDocumentSinceElapsed == 0L) {
                noDocumentSinceElapsed = SystemClock.elapsedRealtime();
            }
            busy.set(false);
            if (SystemClock.elapsedRealtime() - noDocumentSinceElapsed > 120_000L) {
                stopSelf();
            }
            return;
        }
        noDocumentSinceElapsed = 0L;

        SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "").trim();
        String token = settings.getString("mcpToken", "").trim();
        if (endpoint.isEmpty()) {
            busy.set(false);
            return;
        }

        McpClient.waitJob(endpoint, token, jobId, new McpClient.JobStatusCallback() {
            @Override
            public void onStatus(String status, JSONObject fillPlan, String errorMessage) {
                McpBridgeState.contactOk(
                        McpBridgeService.this,
                        "Synchronisé • état serveur : " + status);
                updateNotification("ChatGPT connecté • " + status);
                sendStateBroadcast(jobId);

                if (!"ready".equalsIgnoreCase(status) || fillPlan == null) {
                    busy.set(false);
                    return;
                }

                String commandId = fillPlan.optString("command_id", "");
                AppLog.write(McpBridgeService.this,
                        "MCP_BRIDGE commande reçue " + commandId, null);
                scheduler.execute(() -> processPlan(endpoint, token, jobId, source, fillPlan));
            }

            @Override
            public void onError(String message) {
                McpBridgeState.contactError(McpBridgeService.this, message);
                AppLog.write(McpBridgeService.this,
                        "MCP_BRIDGE connexion erreur : " + message, null);
                updateNotification("ChatGPT • erreur de synchronisation");
                sendStateBroadcast(jobId);
                busy.set(false);
            }
        });
    }

    private void processPlan(String endpoint, String token, String jobId,
                             File sourceFile, JSONObject fillPlan) {
        try {
            String commandId = fillPlan.optString("command_id", "").trim();
            if (commandId.isEmpty()) {
                commandId = "legacy-" + Integer.toHexString(fillPlan.toString().hashCode());
            }

            List<TextOverlay> current = McpBridgeStore.loadOverlays(this, jobId);
            String lastCommand = McpBridgeStore.getLastCommandId(this);
            if (!commandId.equals(lastCommand)) {
                current = applyPlan(fillPlan, current, getPdfPageCount(sourceFile));
                McpBridgeStore.saveOverlays(this, jobId, current);
                applyProfileUpdates(fillPlan.optJSONObject("profile_updates"));
                McpBridgeStore.setLastCommandId(this, commandId);
                McpBridgeState.commandApplied(this, commandId);
                AppLog.write(this, "MCP_BRIDGE commande appliquée " + commandId
                        + " • overlays=" + current.size(), null);
                sendStateBroadcast(jobId);
            }

            JSONArray currentJson = overlaysToJson(current);
            JSONObject profile = buildProfileJson();
            final String finalCommandId = commandId;
            final List<TextOverlay> finalOverlays = new ArrayList<>(current);

            McpClient.acknowledgeApplied(
                    endpoint, token, jobId, commandId,
                    currentJson, profile, 0, 8f,
                    (success, message) -> {
                        if (!success) {
                            AppLog.write(McpBridgeService.this,
                                    "McpBridgeService.ack: " + message, null);
                            busy.set(false);
                            return;
                        }

                        scheduler.execute(() -> {
                            try {
                                uploadPreviewImages(endpoint, token, jobId,
                                        sourceFile, finalOverlays);
                                uploadFinalPdf(endpoint, token, jobId,
                                        sourceFile, finalOverlays);
                                McpBridgeState.contactOk(
                                        McpBridgeService.this,
                                        "Prévisualisation et PDF final envoyés à ChatGPT");
                                AppLog.write(McpBridgeService.this,
                                        "MCP_BRIDGE preview + PDF final envoyés", null);
                                updateNotification("ChatGPT • document synchronisé");
                                sendStateBroadcast(jobId);
                            } catch (Exception e) {
                                AppLog.write(McpBridgeService.this,
                                        "McpBridgeService.preview/final", e);
                            } finally {
                                busy.set(false);
                            }
                        });
                    }
            );
        } catch (Exception e) {
            AppLog.write(this, "McpBridgeService.processPlan", e);
            busy.set(false);
        }
    }

    private List<TextOverlay> applyPlan(JSONObject fillPlan,
                                        List<TextOverlay> current,
                                        int pageCount) throws Exception {
        List<TextOverlay> result = new ArrayList<>();
        if (current != null) result.addAll(current);

        List<TextOverlay> incoming = AiFillPlan.parse(fillPlan.toString(), pageCount);
        String mode = fillPlan.optString("mode", "replace_document");
        int targetPage = fillPlan.optInt("target_page", -1);

        if ("replace_document".equals(mode) || "clear_document".equals(mode)) {
            result.clear();
        } else if ("replace_page".equals(mode) || "clear_page".equals(mode)) {
            result.removeIf(overlay -> overlay.pageIndex == targetPage);
        } else if (!"append".equals(mode)
                && !"update_profile".equals(mode)
                && !"set_editor_state".equals(mode)) {
            throw new IllegalArgumentException("Mode MCP inconnu : " + mode);
        }

        if (!incoming.isEmpty()) result.addAll(incoming);
        return result;
    }

    private void applyProfileUpdates(JSONObject updates) {
        if (updates == null) return;
        try {
            SharedPreferences.Editor editor =
                    getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE).edit();
            String[] keys = new String[]{
                    "firstName", "lastName", "birthDate", "birthPlace", "address",
                    "postalCode", "city", "phone", "email", "otherId"
            };
            for (String key : keys) {
                if (updates.has(key)) {
                    editor.putString(key, updates.optString(key, ""));
                }
            }
            editor.apply();
        } catch (Exception e) {
            AppLog.write(this, "McpBridgeService.profile", e);
        }
    }

    private JSONObject buildProfileJson() throws Exception {
        SharedPreferences profile =
                getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        JSONObject json = new JSONObject();
        String[] keys = new String[]{
                "firstName", "lastName", "birthDate", "birthPlace", "address",
                "postalCode", "city", "phone", "email", "otherId"
        };
        for (String key : keys) {
            json.put(key, profile.getString(key, ""));
        }
        return json;
    }

    private JSONArray overlaysToJson(List<TextOverlay> overlays) throws Exception {
        JSONArray array = new JSONArray();
        for (TextOverlay overlay : overlays) {
            if (overlay == null || overlay.text == null || overlay.text.isEmpty()) continue;
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
            array.put(item);
        }
        return array;
    }

    private void uploadPreviewImages(String endpoint, String token, String jobId,
                                     File sourceFile, List<TextOverlay> overlays) throws Exception {
        Set<Integer> pages = new HashSet<>();
        for (TextOverlay overlay : overlays) {
            if (overlay != null && overlay.pageIndex >= 0) pages.add(overlay.pageIndex);
        }
        if (pages.isEmpty()) pages.add(0);

        try (ParcelFileDescriptor descriptor =
                     ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {

            for (Integer pageIndex : pages) {
                if (pageIndex < 0 || pageIndex >= renderer.getPageCount()) continue;

                PdfRenderer.Page page = renderer.openPage(pageIndex);
                Bitmap bitmap = null;
                Bitmap scaled = null;
                try {
                    int pageWidth = Math.max(1, page.getWidth());
                    int pageHeight = Math.max(1, page.getHeight());
                    float renderScale = Math.min(2.2f,
                            1600f / Math.max(pageWidth, pageHeight));
                    renderScale = Math.max(1f, renderScale);

                    int width = Math.max(1, Math.round(pageWidth * renderScale));
                    int height = Math.max(1, Math.round(pageHeight * renderScale));
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    Canvas canvas = new Canvas(bitmap);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.BLACK);

                    for (TextOverlay overlay : overlays) {
                        if (overlay.pageIndex != pageIndex) continue;
                        drawOverlayExact(canvas, paint, overlay,
                                width, height, renderScale);
                    }

                    scaled = scaleBitmap(bitmap, 1600);
                    byte[] jpeg = encodeJpeg(scaled);
                    McpClient.uploadPreviewImageBlocking(
                            endpoint, token, jobId, pageIndex, jpeg);
                } finally {
                    if (scaled != null && scaled != bitmap && !scaled.isRecycled()) scaled.recycle();
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                    page.close();
                }
            }
        }
    }

    private void uploadFinalPdf(String endpoint, String token, String jobId,
                                File sourceFile, List<TextOverlay> overlays) throws Exception {
        File target = new File(getCacheDir(), "bridge-filled-" + jobId + ".pdf");
        PdfDocument out = new PdfDocument();
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);

        try (ParcelFileDescriptor descriptor =
                     ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {

            for (int i = 0; i < renderer.getPageCount(); i++) {
                PdfRenderer.Page sourcePage = renderer.openPage(i);
                Bitmap bitmap = null;
                PdfDocument.Page dest = null;
                try {
                    int pageWidth = Math.max(1, sourcePage.getWidth());
                    int pageHeight = Math.max(1, sourcePage.getHeight());

                    bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    sourcePage.render(bitmap, null, null,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                            pageWidth, pageHeight, i + 1).create();
                    dest = out.startPage(info);
                    Canvas canvas = dest.getCanvas();
                    canvas.drawBitmap(bitmap, null,
                            new RectF(0f, 0f, pageWidth, pageHeight), bitmapPaint);

                    for (TextOverlay overlay : overlays) {
                        if (overlay.pageIndex != i) continue;
                        drawOverlayExact(canvas, textPaint, overlay,
                                pageWidth, pageHeight, 1f);
                    }

                    out.finishPage(dest);
                    dest = null;
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                    if (dest != null) {
                        try {
                            out.finishPage(dest);
                        } catch (Exception ignored) {
                        }
                    }
                    sourcePage.close();
                }
            }

            try (FileOutputStream output = new FileOutputStream(target, false)) {
                out.writeTo(output);
                output.flush();
            }
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }

        try {
            McpClient.uploadFilledPdf(endpoint, token, jobId, target,
                    (success, message) -> {
                        if (!success) {
                            AppLog.write(McpBridgeService.this,
                                    "McpBridgeService.final: " + message, null);
                        }
                    });
        } finally {
            // McpClient lit le fichier de manière asynchrone: nettoyage différé.
            scheduler.schedule(() -> {
                try {
                    target.delete();
                } catch (Exception ignored) {
                }
            }, 90, TimeUnit.SECONDS);
        }
    }

    private void drawOverlayExact(Canvas canvas, Paint paint, TextOverlay overlay,
                                  int pageWidth, int pageHeight, float scale) {
        paint.setTextSize(Math.max(1f, overlay.textSize * Math.max(0.0001f, scale)));
        paint.setTextAlign(toPaintAlign(overlay.align));

        float x = overlay.x * pageWidth;
        float y = overlay.y * pageHeight;

        if (overlay.isCheckbox()) {
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            y = y - (fm.ascent + fm.descent) * 0.5f;
        }

        canvas.drawText(overlay.text, x, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private Paint.Align toPaintAlign(String align) {
        if (TextOverlay.ALIGN_CENTER.equals(align)) return Paint.Align.CENTER;
        if (TextOverlay.ALIGN_RIGHT.equals(align)) return Paint.Align.RIGHT;
        return Paint.Align.LEFT;
    }

    private int getPdfPageCount(File source) throws Exception {
        try (ParcelFileDescriptor descriptor =
                     ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            return renderer.getPageCount();
        }
    }

    private Bitmap scaleBitmap(Bitmap source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxDimension) return source;

        float scale = maxDimension / (float) largest;
        return Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true);
    }

    private byte[] encodeJpeg(Bitmap bitmap) throws Exception {
        int[] qualities = new int[]{82, 72, 62, 52, 44};
        for (int quality : qualities) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) continue;
            byte[] bytes = output.toByteArray();
            if (bytes.length >= 100 && bytes.length <= 1_450_000) return bytes;
        }
        throw new IllegalStateException("Prévisualisation trop volumineuse");
    }

    private void sendStateBroadcast(String jobId) {
        Intent intent = new Intent(ACTION_UPDATED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_JOB_ID, jobId);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pont ChatGPT",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Maintient le document actif pour les commandes ChatGPT.");
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_app)
                .setContentTitle("Remplissage Papier • ChatGPT")
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String text) {
        // startForeground peut mettre à jour la notification du service sans
        // dépendre d'un appel NotificationManager.notify séparé.
        startForeground(NOTIFICATION_ID, buildNotification(text));
    }
}
