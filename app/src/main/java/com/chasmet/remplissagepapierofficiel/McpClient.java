package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
