package com.chasmet.remplissagepapierofficiel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class McpBridgeStore {
    private static final String PREFS = "mcp_bridge";
    private static final String KEY_ACTIVE_JOB = "active_job_id";
    private static final String KEY_SOURCE_PATH = "source_path";
    private static final String KEY_DOCUMENT_NAME = "document_name";
    private static final String KEY_LAST_COMMAND = "last_command_id";
    private static final long MAX_PDF_BYTES = 25L * 1024L * 1024L;

    private McpBridgeStore() {
    }

    public static synchronized void attachJob(Context context, String jobId, Uri sourceUri,
                                              String documentName,
                                              List<TextOverlay> overlays) throws Exception {
        if (context == null || jobId == null || jobId.trim().isEmpty() || sourceUri == null) {
            throw new IllegalArgumentException("Document MCP incomplet");
        }

        File dir = jobDir(context, jobId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Stockage MCP inaccessible");
        }

        File source = new File(dir, "source.pdf");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean sameJob = jobId.equals(prefs.getString(KEY_ACTIVE_JOB, ""));
        if (!sameJob || !source.isFile()) copyUriToFile(context, sourceUri, source);
        // The bridge may have applied a command while the activity was preparing pages.
        if (!sameJob) saveOverlays(context, jobId, overlays);
        SharedPreferences.Editor edit = prefs.edit()
                .putString(KEY_ACTIVE_JOB, jobId)
                .putString(KEY_SOURCE_PATH, source.getAbsolutePath())
                .putString(KEY_DOCUMENT_NAME,
                        documentName == null || documentName.trim().isEmpty()
                                ? "document.pdf" : documentName.trim());
        if (!sameJob) edit.remove(KEY_LAST_COMMAND);
        edit.apply();
    }

    public static synchronized void attachExistingFile(Context context, String jobId, File sourceFile,
                                                       String documentName,
                                                       List<TextOverlay> overlays) throws Exception {
        if (context == null || jobId == null || jobId.trim().isEmpty()
                || sourceFile == null || !sourceFile.isFile()) {
            throw new IllegalArgumentException("PDF source introuvable");
        }

        File dir = jobDir(context, jobId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Stockage MCP inaccessible");
        }

        File target = new File(dir, "source.pdf");
        if (!sourceFile.getCanonicalFile().equals(target.getCanonicalFile())) {
            try (FileInputStream input = new FileInputStream(sourceFile)) {
                AtomicPdfCopy.copy(input, target, MAX_PDF_BYTES);
            }
        }

        saveOverlays(context, jobId, overlays);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean sameJob = jobId.equals(prefs.getString(KEY_ACTIVE_JOB, ""));
        SharedPreferences.Editor edit = prefs.edit()
                .putString(KEY_ACTIVE_JOB, jobId)
                .putString(KEY_SOURCE_PATH, target.getAbsolutePath())
                .putString(KEY_DOCUMENT_NAME,
                        documentName == null || documentName.trim().isEmpty()
                                ? "document.pdf" : documentName.trim());
        if (!sameJob) edit.remove(KEY_LAST_COMMAND);
        edit.apply();
    }

    public static String getActiveJobId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_JOB, "");
    }

    public static File getSourceFile(Context context, String jobId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (jobId != null && jobId.equals(prefs.getString(KEY_ACTIVE_JOB, ""))) {
            String path = prefs.getString(KEY_SOURCE_PATH, "");
            if (path != null && !path.isEmpty()) {
                File file = new File(path);
                if (file.isFile()) return file;
            }
        }
        File fallback = new File(jobDir(context, jobId), "source.pdf");
        return fallback.isFile() ? fallback : null;
    }

    public static String getDocumentName(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DOCUMENT_NAME, "document.pdf");
    }

    public static synchronized void saveOverlays(Context context, String jobId,
                                                 List<TextOverlay> overlays) throws Exception {
        File dir = jobDir(context, jobId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Stockage MCP inaccessible");
        }

        JSONArray array = new JSONArray();
        if (overlays != null) {
            for (TextOverlay overlay : overlays) {
                if (overlay == null || overlay.text == null || overlay.text.isEmpty()) continue;
                JSONObject item = new JSONObject();
                item.put("overlay_id", overlay.overlayId);
                item.put("page_index", overlay.pageIndex);
                item.put("x", overlay.x);
                item.put("y", overlay.y);
                item.put("text", overlay.text);
                item.put("size", overlay.textSize);
                item.put("align", overlay.align);
                item.put("kind", overlay.kind);
                item.put("data_state", overlay.dataState);
                if (overlay.width > 0f) item.put("width", overlay.width);
                if (overlay.height > 0f) item.put("height", overlay.height);
                array.put(item);
            }
        }

        File file = new File(dir, "overlays.json");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(array.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    public static synchronized List<TextOverlay> loadOverlays(Context context, String jobId) {
        List<TextOverlay> result = new ArrayList<>();
        try {
            File file = new File(jobDir(context, jobId), "overlays.json");
            if (!file.isFile()) return result;

            String json;
            try (FileInputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String text = item.optString("text", "");
                if (text.isEmpty()) continue;

                result.add(new TextOverlay(
                        item.optString("overlay_id", item.optString("id", "")),
                        Math.max(0, item.optInt("page_index", 0)),
                        strict01((float) item.optDouble("x", 0.1), "x"),
                        strict01((float) item.optDouble("y", 0.1), "y"),
                        text,
                        Math.max(4f, Math.min(144f,
                                (float) item.optDouble("size", 8.0))),
                        TextOverlay.normalizeAlign(item.optString(
                                "align", TextOverlay.ALIGN_LEFT)),
                        TextOverlay.normalizeKind(item.optString(
                                "kind", TextOverlay.KIND_TEXT)),
                        optional01((float) item.optDouble("width", 0.0)),
                        optional01((float) item.optDouble("height", 0.0)),
                        TextOverlay.normalizeDataState(item.optString(
                                "data_state", TextOverlay.STATE_KNOWN))
                ));
            }
        } catch (Exception e) {
            AppLog.write(context, "McpBridgeStore.loadOverlays", e);
        }
        return result;
    }

    public static synchronized TextOverlay findOverlay(Context context, String jobId, String overlayId) {
        if (overlayId == null || overlayId.trim().isEmpty()) return null;
        for (TextOverlay overlay : loadOverlays(context, jobId)) {
            if (overlayId.equals(overlay.overlayId)) return overlay;
        }
        return null;
    }

    public static synchronized boolean updateOverlay(Context context, String jobId,
                                                     String overlayId,
                                                     Float x, Float y,
                                                     Float xDelta, Float yDelta,
                                                     Float size, String align,
                                                     String text) throws Exception {
        if (overlayId == null || overlayId.trim().isEmpty()) {
            throw new IllegalArgumentException("overlay_id manquant");
        }
        List<TextOverlay> overlays = loadOverlays(context, jobId);
        boolean changed = false;
        List<TextOverlay> out = new ArrayList<>();
        for (TextOverlay overlay : overlays) {
            if (!overlayId.equals(overlay.overlayId)) {
                out.add(overlay);
                continue;
            }
            float targetX = x == null ? overlay.x : x;
            float targetY = y == null ? overlay.y : y;
            if (xDelta != null) targetX += xDelta;
            if (yDelta != null) targetY += yDelta;
            out.add(overlay.withChanges(targetX, targetY, size, align, text));
            changed = true;
        }
        if (changed) saveOverlays(context, jobId, out);
        return changed;
    }

    public static synchronized boolean deleteOverlay(Context context, String jobId,
                                                     String overlayId) throws Exception {
        if (overlayId == null || overlayId.trim().isEmpty()) {
            throw new IllegalArgumentException("overlay_id manquant");
        }
        List<TextOverlay> overlays = loadOverlays(context, jobId);
        int before = overlays.size();
        overlays.removeIf(overlay -> overlayId.equals(overlay.overlayId));
        if (overlays.size() == before) return false;
        saveOverlays(context, jobId, overlays);
        return true;
    }

    public static String getLastCommandId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_COMMAND, "");
    }

    public static void setLastCommandId(Context context, String commandId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_COMMAND, commandId == null ? "" : commandId)
                .apply();
    }

    public static synchronized void clearActiveJob(Context context, String jobId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String active = prefs.getString(KEY_ACTIVE_JOB, "");
        if (jobId == null || jobId.equals(active)) {
            prefs.edit()
                    .remove(KEY_ACTIVE_JOB)
                    .remove(KEY_SOURCE_PATH)
                    .remove(KEY_DOCUMENT_NAME)
                    .remove(KEY_LAST_COMMAND)
                    .apply();
        }
    }

    private static File jobDir(Context context, String jobId) {
        String safe = jobId == null ? "unknown" : jobId.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(new File(context.getFilesDir(), "mcp-documents"), safe);
    }

    private static void copyUriToFile(Context context, Uri uri, File target) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("PDF source inaccessible");
            AtomicPdfCopy.copy(input, target, MAX_PDF_BYTES);
        }
    }

    private static void copyStream(InputStream input, FileOutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_PDF_BYTES) {
                throw new IllegalArgumentException("PDF trop volumineux");
            }
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static float strict01(float value, String name) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(name + " hors limites");
        }
        return value;
    }

    private static float optional01(float value) {
        if (value <= 0f) return 0f;
        return strict01(value, "width/height");
    }
}
