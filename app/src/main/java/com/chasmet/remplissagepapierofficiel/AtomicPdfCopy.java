package com.chasmet.remplissagepapierofficiel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Replace a source only after the complete copy has been validated and flushed. */
final class AtomicPdfCopy {
    private AtomicPdfCopy() { }

    static void copy(InputStream input, File target, long maxBytes) throws IOException {
        File temporary = File.createTempFile("pdf-copy-", ".tmp", target.getParentFile());
        try {
            long total = 0;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) throw new IOException("PDF trop volumineux");
                    output.write(buffer, 0, read);
                }
                if (total < 5) throw new IOException("PDF source invalide");
                output.getFD().sync();
            }
            // Same-directory rename is atomic on Android's app-private filesystem.
            if (!temporary.renameTo(target)) throw new IOException("Remplacement du PDF impossible");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }
}
