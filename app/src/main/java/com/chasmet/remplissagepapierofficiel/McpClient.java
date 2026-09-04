package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class McpClient {
    public interface Callback {
        void onResult(boolean success, String message);
    }

    public interface JobCallback {
        void onCreated(String jobId, String status);
        void onError(String message);
    }

    public interface JobStatusCallback {
        void onStatus(String status, JSONObject fillPlan, String errorMessage);
        void onError(String message);
    }

    public interface InboxCallback {
        void onInbox(JSONObject document);
        void onEmpty();
        void onError(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int MAX_RESPONSE_CHARS = 3_000_000;

    private McpClient() {
    }

    public static void testConnection(String endpoint, String token, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject clientInfo = new JSONObject();
                clientInfo.put("name", "Remplissage papier officiel");
                clientInfo.put("version", BuildConfig.VERSION_NAME);

                JSONObject params = new JSONObject();
                params.put("protocolVersion", "2025-06-18");
                params.put("capabilities", new JSONObject());
                params.put("clientInfo", clientInfo);

                JSONObject request = new JSONObject();
                request.put("jsonrpc", "2.0");
                request.put("id", 1);
                request.put("method", "initialize");
                request.put("params", params);

                HttpResult result = postJson(endpoint, token, request, 15000, 15000);
                if (result.code >= 200 && result.code < 300) {
                    JSONObject json = new JSONObject(result.body);
                    if (json.has("result")) {
                        callback.onResult(true, "Connexion MCP réussie (HTTP " + result.code + ")");
                    } else {
                        callback.onResult(false, "Réponse MCP invalide");
                    }
                } else {
                    callback.onResult(false, "MCP a répondu HTTP " + result.code
                            + (result.body.isEmpty() ? "" : " : " + abbreviate(result.body, 1000)));
                }
            } catch (Exception e) {
                callback.onResult(false, "Erreur MCP : " + safeMessage(e));
            }
        });
    }

    public static void createJob(String endpoint, String token,
                                 JSONObject document, JSONObject profile, JSONArray fieldHints,
                                 JobCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("action", "create_job");
                request.put("document", document == null ? new JSONObject() : document);
                request.put("profile", profile == null ? new JSONObject() : profile);
                request.put("field_hints", fieldHints == null ? new JSONArray() : fieldHints);

                HttpResult result = postJson(endpoint, token, request, 20000, 60000);
                if (result.code < 200 || result.code >= 300) {
                    callback.onError("Envoi refusé (HTTP " + result.code + ") : "
                            + abbreviate(result.body, 1200));
                    return;
                }

                JSONObject root = new JSONObject(result.body);
                if (!root.optBoolean("ok", false)) {
                    callback.onError(root.optString("error", "Le serveur a refusé le document"));
                    return;
                }
                JSONObject job = root.optJSONObject("job");
                if (job == null) {
                    callback.onError("Le serveur n’a pas renvoyé l’identifiant du document");
                    return;
                }
                String jobId = job.optString("id", "").trim();
                if (jobId.isEmpty()) {
                    callback.onError("Identifiant de document MCP manquant");
                    return;
                }
                callback.onCreated(jobId, job.optString("status", "pending"));
            } catch (Exception e) {
                callback.onError("Erreur d’envoi MCP : " + safeMessage(e));
            }
        });
    }

    public static void getJob(String endpoint, String token, String jobId,
                              JobStatusCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("action", "get_job");
                request.put("job_id", jobId);

                HttpResult result = postJson(endpoint, token, request, 15000, 30000);
                if (result.code < 200 || result.code >= 300) {
                    callback.onError("Lecture refusée (HTTP " + result.code + ") : "
                            + abbreviate(result.body, 1200));
                    return;
                }

                JSONObject root = new JSONObject(result.body);
                if (!root.optBoolean("ok", false)) {
                    callback.onError(root.optString("error", "Document MCP introuvable"));
                    return;
                }
                JSONObject job = root.optJSONObject("job");
                if (job == null) {
                    callback.onError("Réponse MCP incomplète");
                    return;
                }

                callback.onStatus(
                        job.optString("status", "pending"),
                        job.optJSONObject("fill_plan"),
                        job.optString("error_message", "")
                );
            } catch (Exception e) {
                callback.onError("Erreur de lecture MCP : " + safeMessage(e));
            }
        });
    }

    public static void updateJobContext(String endpoint, String token, String jobId,
                                        JSONObject document, JSONObject profile, JSONArray fieldHints,
                                        Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("action", "update_job_context");
                request.put("job_id", jobId);
                request.put("document", document == null ? new JSONObject() : document);
                request.put("profile", profile == null ? new JSONObject() : profile);
                request.put("field_hints", fieldHints == null ? new JSONArray() : fieldHints);

                HttpResult result = postJson(endpoint, token, request, 20000, 60000);
                if (result.code < 200 || result.code >= 300) {
                    callback.onResult(false, "Synchronisation refusée (HTTP " + result.code + ") : "
                            + abbreviate(result.body, 1200));
                    return;
                }
                JSONObject root = new JSONObject(result.body);
                if (!root.optBoolean("ok", false)) {
                    callback.onResult(false, root.optString("error", "Synchronisation refusée"));
                    return;
                }
                callback.onResult(true, root.optString("status", "pending"));
            } catch (Exception e) {
                callback.onResult(false, "Erreur de synchronisation MCP : " + safeMessage(e));
            }
        });
    }

    public static void getInbox(String endpoint, String token, InboxCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("action", "get_inbox");

                HttpResult result = postJson(endpoint, token, request, 15000, 30000);
                if (result.code < 200 || result.code >= 300) {
                    callback.onError("Boîte ChatGPT indisponible (HTTP " + result.code + ")");
                    return;
                }

                JSONObject root = new JSONObject(result.body);
                if (!root.optBoolean("ok", false)) {
                    callback.onError(root.optString("error", "Boîte ChatGPT indisponible"));
                    return;
                }

                JSONObject document = root.optJSONObject("document");
                if (document == null) {
                    callback.onEmpty();
                } else {
                    callback.onInbox(document);
                }
            } catch (Exception e) {
                callback.onError("Erreur boîte ChatGPT : " + safeMessage(e));
            }
        });
    }

    public static void downloadPdf(String downloadUrl, File target, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/pdf");

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    callback.onResult(false, "Téléchargement PDF refusé (HTTP " + code + ")");
                    return;
                }

                long declared = connection.getContentLength();
                if (declared > 25L * 1024L * 1024L) {
                    callback.onResult(false, "PDF trop volumineux");
                    return;
                }

                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    callback.onResult(false, "Stockage local inaccessible");
                    return;
                }

                byte[] buffer = new byte[8192];
                long total = 0L;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(target, false)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > 25L * 1024L * 1024L) {
                            throw new IllegalStateException("PDF trop volumineux");
                        }
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                }

                try (FileInputStream check = new FileInputStream(target)) {
                    byte[] magic = new byte[4];
                    if (check.read(magic) != 4
                            || magic[0] != 0x25 || magic[1] != 0x50
                            || magic[2] != 0x44 || magic[3] != 0x46) {
                        target.delete();
                        callback.onResult(false, "Fichier reçu invalide");
                        return;
                    }
                }

                callback.onResult(true, target.getAbsolutePath());
            } catch (Exception e) {
                target.delete();
                callback.onResult(false, "Erreur téléchargement PDF : " + safeMessage(e));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static void uploadFilledPdf(String endpoint, String token, String jobId,
                                       File pdfFile, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (endpoint == null || !endpoint.startsWith("https://")) {
                    throw new IllegalArgumentException("URL MCP invalide");
                }
                if (pdfFile == null || !pdfFile.isFile()) {
                    throw new IllegalArgumentException("PDF final introuvable");
                }
                if (pdfFile.length() > 25L * 1024L * 1024L) {
                    throw new IllegalArgumentException("PDF final trop volumineux");
                }

                String separator = endpoint.contains("?") ? "&" : "?";
                String uploadUrl = endpoint + separator
                        + "app_action=upload_filled_pdf&job_id="
                        + URLEncoder.encode(jobId, "UTF-8");

                connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/pdf");
                connection.setRequestProperty("Accept", "application/json");
                if (token != null && !token.trim().isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token.trim());
                }
                connection.setFixedLengthStreamingMode(pdfFile.length());

                byte[] buffer = new byte[8192];
                try (FileInputStream input = new FileInputStream(pdfFile);
                     OutputStream output = connection.getOutputStream()) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                String body = readBody(stream);
                if (code < 200 || code >= 300) {
                    callback.onResult(false, "Envoi PDF final refusé (HTTP " + code + ") : "
                            + abbreviate(body, 1000));
                    return;
                }
                JSONObject root = new JSONObject(body);
                callback.onResult(root.optBoolean("ok", false),
                        root.optBoolean("ok", false)
                                ? "PDF final disponible dans ChatGPT"
                                : root.optString("error", "Envoi PDF final refusé"));
            } catch (Exception e) {
                callback.onResult(false, "Erreur envoi PDF final : " + safeMessage(e));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static void uploadPageImageBlocking(String endpoint, String token,
                                               String jobId, int pageIndex,
                                               byte[] jpegBytes) throws Exception {
        if (endpoint == null || !endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("URL MCP invalide");
        }
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new IllegalArgumentException("jobId manquant");
        }
        if (pageIndex < 0 || pageIndex > 999) {
            throw new IllegalArgumentException("pageIndex invalide");
        }
        if (jpegBytes == null || jpegBytes.length < 100 || jpegBytes.length > 1_500_000) {
            throw new IllegalArgumentException("Image JPEG invalide ou trop volumineuse");
        }

        HttpURLConnection connection = null;
        try {
            String separator = endpoint.contains("?") ? "&" : "?";
            String uploadUrl = endpoint + separator
                    + "app_action=upload_page&job_id="
                    + URLEncoder.encode(jobId, "UTF-8")
                    + "&page_index=" + pageIndex;

            connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "image/jpeg");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "RemplissagePapierOfficiel/" + BuildConfig.VERSION_NAME);
            if (token != null && !token.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            }
            connection.setFixedLengthStreamingMode(jpegBytes.length);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(jpegBytes);
                output.flush();
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readBody(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Image page refusée (HTTP " + code + ") : "
                        + abbreviate(body, 800));
            }

            JSONObject root = new JSONObject(body);
            if (!root.optBoolean("ok", false)) {
                throw new IllegalStateException(
                        root.optString("error", "Image page refusée par le serveur"));
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static void deactivateJob(String endpoint, String token, String jobId, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("action", "deactivate_job");
                request.put("job_id", jobId);

                HttpResult result = postJson(endpoint, token, request, 12000, 20000);
                if (result.code < 200 || result.code >= 300) {
                    callback.onResult(false, "Désactivation refusée (HTTP " + result.code + ")");
                    return;
                }
                JSONObject root = new JSONObject(result.body);
                callback.onResult(root.optBoolean("ok", false),
                        root.optBoolean("ok", false)
                                ? "Document désactivé"
                                : root.optString("error", "Désactivation refusée"));
            } catch (Exception e) {
                callback.onResult(false, "Erreur désactivation MCP : " + safeMessage(e));
            }
        });
    }

    public static void acknowledgeApplied(String endpoint, String token, String jobId,
                                          String commandId, JSONArray currentOverlays,
                                          JSONObject profile, int currentPageIndex,
                                          float currentTextSize, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("action", "ack_applied");
                request.put("job_id", jobId);
                request.put("command_id", commandId == null ? "" : commandId);
                request.put("current_overlays",
                        currentOverlays == null ? new JSONArray() : currentOverlays);
                request.put("profile", profile == null ? new JSONObject() : profile);
                request.put("current_page_index", currentPageIndex);
                request.put("current_text_size", currentTextSize);

                HttpResult result = postJson(endpoint, token, request, 15000, 30000);
                if (result.code < 200 || result.code >= 300) {
                    callback.onResult(false, "Confirmation refusée (HTTP " + result.code + ") : "
                            + abbreviate(result.body, 1200));
                    return;
                }

                JSONObject root = new JSONObject(result.body);
                if (!root.optBoolean("ok", false)) {
                    callback.onResult(false,
                            root.optString("error", "Le serveur n’a pas confirmé l’application"));
                    return;
                }
                callback.onResult(true, "Application confirmée");
            } catch (Exception e) {
                callback.onResult(false, "Erreur de confirmation MCP : " + safeMessage(e));
            }
        });
    }

    private static HttpResult postJson(String endpoint, String token, JSONObject payload,
                                       int connectTimeout, int readTimeout) throws Exception {
        if (endpoint == null || !endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("L’URL MCP doit commencer par https://");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            connection.setRequestProperty("User-Agent",
                    "RemplissagePapierOfficiel/" + BuildConfig.VERSION_NAME);
            if (token != null && !token.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return new HttpResult(code, readBody(stream));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (response.length() + line.length() > MAX_RESPONSE_CHARS) {
                    throw new IllegalStateException("Réponse serveur trop volumineuse");
                }
                response.append(line);
            }
        }
        return response.toString();
    }

    private static String abbreviate(String value, int max) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "erreur inconnue";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
