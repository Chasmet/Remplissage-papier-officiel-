package com.chasmet.remplissagepapierofficiel;

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

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private McpClient() {
    }

    public static void testConnection(String endpoint, String token, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (endpoint == null || !endpoint.startsWith("https://")) {
                    callback.onResult(false, "L’URL MCP doit commencer par https://");
                    return;
                }

                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json, text/event-stream");
                connection.setRequestProperty("User-Agent", "RemplissagePapierOfficiel/1.0");
                if (token != null && !token.trim().isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token.trim());
                }

                JSONObject clientInfo = new JSONObject();
                clientInfo.put("name", "Remplissage papier officiel");
                clientInfo.put("version", "1.0.0");

                JSONObject params = new JSONObject();
                params.put("protocolVersion", "2025-06-18");
                params.put("capabilities", new JSONObject());
                params.put("clientInfo", clientInfo);

                JSONObject request = new JSONObject();
                request.put("jsonrpc", "2.0");
                request.put("id", 1);
                request.put("method", "initialize");
                request.put("params", params);

                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body);
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null && response.length() < 2000) {
                            response.append(line).append('\n');
                        }
                    }
                }

                if (code >= 200 && code < 300) {
                    callback.onResult(true, "Connexion MCP réussie (HTTP " + code + ")");
                } else {
                    callback.onResult(false, "MCP a répondu HTTP " + code + (response.length() > 0 ? " : " + response.toString().trim() : ""));
                }
            } catch (Exception e) {
                callback.onResult(false, "Erreur MCP : " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
