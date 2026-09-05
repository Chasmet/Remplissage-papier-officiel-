package com.chasmet.remplissagepapierofficiel;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public final class AtomicPdfCopyTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("pdf-copy-test-");
        File target = dir.resolve("source.pdf").toFile();
        byte[] original = "%PDF-1.4 original document".getBytes("UTF-8");
        try {
            Files.write(target.toPath(), original);
            try (InputStream input = new FileInputStream(target)) {
                AtomicPdfCopy.copy(input, target, 1000);
            }
            same(target, original); // Regression: copying onto itself used to truncate the PDF.

            InputStream interrupted = new InputStream() {
                int count;
                public int read() throws IOException {
                    if (++count > 10) throw new IOException("Connection lost");
                    return 'x';
                }
            };
            expectFailure(interrupted, target, 1000);
            same(target, original);
            expectFailure(new ByteArrayInputStream(new byte[100]), target, 20);
            same(target, original);
            expectFailure(new ByteArrayInputStream(new byte[0]), target, 1000);
            same(target, original);

            byte[] replacement = "%PDF-1.4 replacement".getBytes("UTF-8");
            AtomicPdfCopy.copy(new ByteArrayInputStream(replacement), target, 1000);
            same(target, replacement);
            try (java.util.stream.Stream<Path> files = Files.list(dir)) {
                if (files.count() != 1) throw new AssertionError("Temporary file leaked");
            }
            System.out.println("PASS: self-copy, interrupted copy, size limit, empty input, replacement, cleanup");
        } finally {
            Files.deleteIfExists(target.toPath());
            Files.deleteIfExists(dir);
        }
    }

    private static void expectFailure(InputStream input, File target, long max) throws Exception {
        try {
            AtomicPdfCopy.copy(input, target, max);
            throw new AssertionError("Failure expected");
        } catch (IOException expected) { }
    }

    private static void same(File target, byte[] expected) throws Exception {
        if (!Arrays.equals(Files.readAllBytes(target.toPath()), expected)) {
            throw new AssertionError("PDF changed unexpectedly");
        }
    }
}
