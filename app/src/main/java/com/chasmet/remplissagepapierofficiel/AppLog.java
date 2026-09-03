package com.chasmet.remplissagepapierofficiel;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppLog {
    private static final long MAX_BYTES = 256L * 1024L;
    private static final String FILE_NAME = "stability-diagnostics.log";

    private AppLog() {
    }

    public static synchronized void write(Context context, String area, Throwable error) {
        if (context == null) return;
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (file.exists() && file.length() > MAX_BYTES) {
                File old = new File(context.getFilesDir(), FILE_NAME + ".old");
                if (old.exists()) old.delete();
                file.renameTo(old);
            }

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.FRANCE).format(new Date());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                writer.write(time + " | " + BuildConfig.VERSION_NAME + " | " + safe(area));
                writer.newLine();
                if (error != null) {
                    StringWriter stack = new StringWriter();
                    error.printStackTrace(new PrintWriter(stack));
                    String[] lines = stack.toString().split("\\r?\\n");
                    int max = Math.min(lines.length, 12);
                    for (int i = 0; i < max; i++) {
                        writer.write("  " + lines[i]);
                        writer.newLine();
                    }
                }
                writer.flush();
            }
        } catch (Exception ignored) {
            // Diagnostics must never be able to crash the application.
        }
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
