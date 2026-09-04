package com.chasmet.remplissagepapierofficiel;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class McpBridgeState {
    private static final String PREFS = "mcp_bridge_status";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_CONNECTED = "connected";
    private static final String KEY_LAST_CONTACT = "last_contact";
    private static final String KEY_LAST_EVENT = "last_event";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_COMMAND = "last_command";
    private static final String KEY_CHATGPT_CONNECTED = "chatgpt_connected";
    private static final String KEY_CHATGPT_LAST_SEEN = "chatgpt_last_seen";

    private McpBridgeState() {
    }

    public static void setRunning(Context context, boolean running) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUNNING, running)
                .apply();
    }

    public static void contactOk(Context context, String event) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUNNING, true)
                .putBoolean(KEY_CONNECTED, true)
                .putLong(KEY_LAST_CONTACT, System.currentTimeMillis())
                .putString(KEY_LAST_EVENT, event == null ? "Synchronisation OK" : event)
                .putString(KEY_LAST_ERROR, "")
                .apply();
    }

    public static void contactError(Context context, String error) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUNNING, true)
                .putBoolean(KEY_CONNECTED, false)
                .putLong(KEY_LAST_CONTACT, System.currentTimeMillis())
                .putString(KEY_LAST_EVENT, "Erreur de synchronisation")
                .putString(KEY_LAST_ERROR, error == null ? "Erreur inconnue" : error)
                .apply();
    }

    public static void commandApplied(Context context, String commandId) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_COMMAND, commandId == null ? "" : commandId)
                .putString(KEY_LAST_EVENT, "Commande ChatGPT appliquée")
                .putLong(KEY_LAST_CONTACT, System.currentTimeMillis())
                .putBoolean(KEY_CONNECTED, true)
                .apply();
    }

    public static void setChatGptPresence(Context context, boolean connected, long lastSeenMillis) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CHATGPT_CONNECTED, connected)
                .putLong(KEY_CHATGPT_LAST_SEEN, Math.max(0L, lastSeenMillis))
                .apply();
    }

    public static Snapshot read(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Snapshot(
                p.getBoolean(KEY_RUNNING, false),
                p.getBoolean(KEY_CONNECTED, false),
                p.getLong(KEY_LAST_CONTACT, 0L),
                p.getString(KEY_LAST_EVENT, ""),
                p.getString(KEY_LAST_ERROR, ""),
                p.getString(KEY_LAST_COMMAND, ""),
                p.getBoolean(KEY_CHATGPT_CONNECTED, false),
                p.getLong(KEY_CHATGPT_LAST_SEEN, 0L)
        );
    }

    public static String oneLine(Context context) {
        Snapshot s = read(context);
        String activeJob = McpBridgeStore.getActiveJobId(context);

        if (activeJob == null || activeJob.isEmpty()) {
            return "ChatGPT : aucun document synchronisé";
        }
        String age = formatAge(s.lastContact);
        boolean fresh = s.lastContact > 0L
                && System.currentTimeMillis() - s.lastContact <= 30_000L;

        if (!s.running) {
            return "ChatGPT : pont arrêté • appuyez sur SYNCHRO";
        }
        boolean chatFresh = s.chatGptConnected
                && s.chatGptLastSeen > 0L
                && System.currentTimeMillis() - s.chatGptLastSeen <= 90_000L;

        if (s.connected && fresh && chatFresh) {
            return "ChatGPT : CONNECTÉ • synchro active";
        }
        if (s.connected && fresh) {
            return "Pont prêt • ouvrez ChatGPT";
        }
        if (s.connected) {
            return "ChatGPT : contact serveur ancien • " + age;
        }
        return "ChatGPT : ERREUR • " + age;
    }

    public static String diagnostic(Context context) {
        Snapshot s = read(context);
        String job = McpBridgeStore.getActiveJobId(context);
        StringBuilder out = new StringBuilder();
        out.append("Version APK : ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        boolean fresh = s.lastContact > 0L
                && System.currentTimeMillis() - s.lastContact <= 30_000L;
        SharedPreferences settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String endpoint = settings.getString("mcpUrl", "");
        File source = McpBridgeStore.getSourceFile(context, job);

        out.append("Pont service : ").append(s.running ? "ACTIF" : "ARRÊTÉ").append("\n");
        out.append("Connexion MCP : ")
                .append(s.connected && fresh ? "OK / RÉCENTE" : "NON CONFIRMÉE OU ANCIENNE")
                .append("\n");
        out.append("URL MCP : ").append(endpoint == null || endpoint.isEmpty() ? "non configurée" : endpoint).append("\n");
        out.append("Document actif : ").append(job == null || job.isEmpty() ? "aucun" : job).append("\n");
        out.append("Nom document : ").append(McpBridgeStore.getDocumentName(context)).append("\n");
        out.append("Copie PDF interne : ").append(source != null && source.isFile() ? "OK" : "ABSENTE").append("\n");
        out.append("Présence ChatGPT : ")
                .append(s.chatGptConnected ? "VUE RÉCEMMENT" : "NON DÉTECTÉE")
                .append("\n");
        out.append("Dernier passage ChatGPT : ").append(formatDate(s.chatGptLastSeen)).append("\n");
        out.append("Dernier contact Android→MCP : ").append(formatDate(s.lastContact)).append("\n");
        out.append("Dernier événement : ").append(empty(s.lastEvent)).append("\n");
        out.append("Dernière commande : ").append(empty(s.lastCommand)).append("\n");
        out.append("Dernière erreur : ").append(empty(s.lastError));
        return out.toString();
    }

    private static String formatAge(long timestamp) {
        if (timestamp <= 0L) return "jamais synchronisé";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);
        if (seconds < 5L) return "à l'instant";
        if (seconds < 60L) return "il y a " + seconds + " s";
        long minutes = seconds / 60L;
        if (minutes < 60L) return "il y a " + minutes + " min";
        return "il y a " + (minutes / 60L) + " h";
    }

    private static String formatDate(long timestamp) {
        if (timestamp <= 0L) return "jamais";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)
                .format(new Date(timestamp));
    }

    private static String empty(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    public static final class Snapshot {
        public final boolean running;
        public final boolean connected;
        public final long lastContact;
        public final String lastEvent;
        public final String lastError;
        public final String lastCommand;
        public final boolean chatGptConnected;
        public final long chatGptLastSeen;

        Snapshot(boolean running, boolean connected, long lastContact,
                 String lastEvent, String lastError, String lastCommand,
                 boolean chatGptConnected, long chatGptLastSeen) {
            this.running = running;
            this.connected = connected;
            this.lastContact = lastContact;
            this.lastEvent = lastEvent;
            this.lastError = lastError;
            this.lastCommand = lastCommand;
            this.chatGptConnected = chatGptConnected;
            this.chatGptLastSeen = chatGptLastSeen;
        }
    }
}
