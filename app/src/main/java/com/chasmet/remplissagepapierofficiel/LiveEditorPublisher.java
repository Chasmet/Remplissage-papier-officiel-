package com.chasmet.remplissagepapierofficiel;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publie en continu une vue fidèle du document tel qu'il est actuellement dans
 * l'application. Cette vue utilise exactement les mêmes coordonnées et les
 * mêmes tailles que le moteur PDF final. ChatGPT peut donc contrôler le rendu
 * réel sans attendre un export manuel.
 */
public final class LiveEditorPublisher {
    private static final String SETTINGS_PREFS = "settings";
    private static final long PERIOD_SECONDS = 2L;
    private static final int MAX_PREVIEW_BYTES = 1_500_000;

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static ScheduledExecutorService scheduler;

    private static volatile String lastFingerprint = "";
    private static String lastJobId = "";
    private static Set<Integer> lastPublishedPages = new HashSet<>();

    private LiveEditorPublisher() {
    }

    public static void start(Context context) {
        if (context == null || !STARTED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "live-editor-publisher");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                () -> publishSafely(app),
                1L,
                PERIOD_SECONDS,
                TimeUnit.SECONDS);
        AppLog.write(app, "LIVE_EDITOR démarré", null);
    }

    private static void publishSafely(Context context) {
        if (!RUNNING.compareAndSet(false, true)) return;
        try {
            publishIfChanged(context);
        } catch (Throwable error) {
            AppLog.write(context, "LiveEditorPublisher.publish", error);
        } finally {
            RUNNING.set(false);
        }
    }

    private static void publishIfChanged(Context context) throws Exception {
        String jobId = McpBridgeStore.getActiveJobId(context);
        if (jobId == null || jobId.trim().isEmpty()) return;

        File source = McpBridgeStore.getSourceFile(context, jobId);
        if (source == null || !source.isFile()) return;

        SharedPreferences settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "");
        String token = settings.getString("mcpToken", "");
        endpoint = endpoint == null ? "" : endpoint.trim();
        token = token == null ? "" : token.trim();
        if (!endpoint.startsWith("https://")) return;

        List<TextOverlay> overlays = McpBridgeStore.loadOverlays(context, jobId);
        String commandId = McpBridgeStore.getLastCommandId(context);
        String fingerprint = buildFingerprint(jobId, source, commandId, overlays);
        if (fingerprint.equals(lastFingerprint)) return;

        Set<Integer> pages = new HashSet<>();
        pages.add(0);
        // Re-publish pages whose last overlay was deleted, so old text disappears remotely.
        if (jobId.equals(lastJobId)) pages.addAll(lastPublishedPages);
        for (TextOverlay overlay : overlays) {
            if (overlay != null && overlay.pageIndex >= 0) pages.add(overlay.pageIndex);
        }

        int sent = 0;
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                source, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            for (Integer pageIndex : pages) {
                if (pageIndex == null || pageIndex < 0 || pageIndex >= renderer.getPageCount()) continue;
                byte[] jpeg = renderPage(renderer, pageIndex, overlays);
                uploadPreview(endpoint, token, jobId, pageIndex, jpeg);
                sent++;
            }
        }

        if (sent > 0) {
            lastFingerprint = fingerprint;
            lastJobId = jobId;
            lastPublishedPages = new HashSet<>(pages);
            AppLog.write(context,
                    "LIVE_EDITOR publié • pages=" + sent + " • overlays=" + overlays.size(),
                    null);
        }
    }

    private static String buildFingerprint(String jobId, File source,
                                           String commandId, List<TextOverlay> overlays) {
        StringBuilder out = new StringBuilder();
        out.append(jobId).append('|')
                .append(source.length()).append('|')
                .append(source.lastModified()).append('|')
                .append(commandId == null ? "" : commandId).append('|');
        for (TextOverlay overlay : overlays) {
            if (overlay == null) continue;
            out.append(overlay.pageIndex).append(':')
                    .append(overlay.x).append(':')
                    .append(overlay.y).append(':')
                    .append(overlay.textSize).append(':')
                    .append(overlay.align).append(':')
                    .append(overlay.kind).append(':')
                    .append(overlay.text).append(';');
        }
        return Integer.toHexString(out.toString().hashCode());
    }

    private static byte[] renderPage(PdfRenderer renderer, int pageIndex,
                                     List<TextOverlay> overlays) throws Exception {
        PdfRenderer.Page page = renderer.openPage(pageIndex);
        Bitmap bitmap = null;
        Bitmap scaled = null;
        try {
            int pageWidth = Math.max(1, page.getWidth());
            int pageHeight = Math.max(1, page.getHeight());
            float renderScale = Math.min(2.4f,
                    1800f / Math.max(pageWidth, pageHeight));
            renderScale = Math.max(1f, renderScale);

            int width = Math.max(1, Math.round(pageWidth * renderScale));
            int height = Math.max(1, Math.round(pageHeight * renderScale));
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            paint.setColor(Color.BLACK);
            for (TextOverlay overlay : overlays) {
                if (overlay == null || overlay.pageIndex != pageIndex) continue;
                drawOverlayExact(canvas, paint, overlay, width, height, renderScale);
            }

            scaled = scaleBitmap(bitmap, 1600);
            return encodeJpeg(scaled);
        } finally {
            if (scaled != null && scaled != bitmap && !scaled.isRecycled()) scaled.recycle();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            page.close();
        }
    }

    private static void drawOverlayExact(Canvas canvas, Paint paint, TextOverlay overlay,
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

    private static Paint.Align toPaintAlign(String align) {
        if (TextOverlay.ALIGN_CENTER.equals(align)) return Paint.Align.CENTER;
        if (TextOverlay.ALIGN_RIGHT.equals(align)) return Paint.Align.RIGHT;
        return Paint.Align.LEFT;
    }

    private static Bitmap scaleBitmap(Bitmap source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxDimension) return source;
        float scale = maxDimension / (float) largest;
        return Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
    }

    private static byte[] encodeJpeg(Bitmap bitmap) throws Exception {
        int[] qualities = new int[]{86, 78, 70, 62, 54, 46};
        for (int quality : qualities) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) continue;
            byte[] bytes = output.toByteArray();
            if (bytes.length >= 100 && bytes.length <= MAX_PREVIEW_BYTES) return bytes;
        }
        throw new IllegalStateException("Vue live trop volumineuse");
    }

    private static void uploadPreview(String endpoint, String token, String jobId,
                                      int pageIndex, byte[] jpeg) throws Exception {
        String separator = endpoint.contains("?") ? "&" : "?";
        String uploadUrl = endpoint + separator
                + "app_action=upload_preview_page&job_id="
                + URLEncoder.encode(jobId, "UTF-8")
                + "&page_index=" + pageIndex;

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "image/jpeg");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "RemplissagePapierOfficiel/" + BuildConfig.VERSION_NAME + " LiveEditor");
            if (!token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            connection.setFixedLengthStreamingMode(jpeg.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(jpeg);
                output.flush();
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readSmallBody(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Vue live refusée HTTP " + code + " : " + body);
            }
            JSONObject root = new JSONObject(body);
            if (!root.optBoolean("ok", false)) {
                throw new IllegalStateException(root.optString("error", "Vue live refusée"));
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readSmallBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        byte[] buffer = new byte[8192];
        int total = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int read;
        while ((read = stream.read(buffer)) != -1) {
            total += read;
            if (total > 64 * 1024) throw new IllegalStateException("Réponse serveur trop volumineuse");
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }
}
