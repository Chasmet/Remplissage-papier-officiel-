package com.chasmet.remplissagepapierofficiel;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class FormFieldDetector {
    private static final class Segment {
        int x1;
        int x2;
        int y1;
        int y2;

        Segment(int x1, int x2, int y) {
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y;
            this.y2 = y;
        }

        int width() {
            return x2 - x1 + 1;
        }

        int height() {
            return y2 - y1 + 1;
        }

        float centerY() {
            return (y1 + y2) * 0.5f;
        }
    }

    private FormFieldDetector() {
    }

    public static List<FormField> detect(Bitmap bitmap, int pageIndex) {
        if (bitmap == null || bitmap.isRecycled()) return Collections.emptyList();

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        if (width < 100 || height < 100) return Collections.emptyList();

        final int left = Math.max(2, Math.round(width * 0.025f));
        final int right = Math.min(width - 3, Math.round(width * 0.975f));
        final int top = Math.max(2, Math.round(height * 0.045f));
        final int bottom = Math.min(height - 3, Math.round(height * 0.955f));
        final int minRun = Math.max(48, Math.round(width * 0.075f));
        final int maxRun = Math.round(width * 0.92f);
        final int maxGap = Math.max(1, Math.round(width / 900f));

        List<Segment> raw = new ArrayList<>();
        int[] row = new int[width];

        for (int y = top; y <= bottom; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            int runStart = -1;
            int lastInk = -1;
            int gap = 0;

            for (int x = left; x <= right; x++) {
                if (isLinePixel(row[x])) {
                    if (runStart < 0) runStart = x;
                    lastInk = x;
                    gap = 0;
                } else if (runStart >= 0) {
                    gap++;
                    if (gap > maxGap) {
                        addRun(raw, runStart, lastInk, y, minRun, maxRun);
                        runStart = -1;
                        lastInk = -1;
                        gap = 0;
                    }
                }
            }
            if (runStart >= 0) addRun(raw, runStart, lastInk, y, minRun, maxRun);
        }

        if (raw.isEmpty()) return Collections.emptyList();

        List<Segment> merged = mergeRows(raw, width, height);
        List<FormField> result = new ArrayList<>();
        boolean[] consumed = new boolean[merged.size()];

        // Rectangular fields: two thin horizontal borders with almost identical width.
        for (int i = 0; i < merged.size(); i++) {
            Segment topLine = merged.get(i);
            if (!isUsefulLine(topLine, width, height)) continue;
            for (int j = i + 1; j < merged.size(); j++) {
                Segment bottomLine = merged.get(j);
                float dy = bottomLine.centerY() - topLine.centerY();
                if (dy > height * 0.085f) break;
                if (dy < height * 0.012f) continue;
                if (!isUsefulLine(bottomLine, width, height)) continue;

                float overlap = overlapRatio(topLine, bottomLine);
                float widthRatio = Math.min(topLine.width(), bottomLine.width())
                        / (float) Math.max(topLine.width(), bottomLine.width());
                if (overlap < 0.90f || widthRatio < 0.88f) continue;

                int x1 = Math.max(topLine.x1, bottomLine.x1);
                int x2 = Math.min(topLine.x2, bottomLine.x2);
                if (x2 - x1 < minFieldWidth(width)) continue;

                float fx = x1 / (float) width;
                float fy = Math.max(0f, (topLine.y2 + 1) / (float) height);
                float fw = (x2 - x1 + 1) / (float) width;
                float fh = Math.max(0.012f, (bottomLine.y1 - topLine.y2 - 1) / (float) height);
                result.add(new FormField(pageIndex, fx, fy, fw, fh, FormField.Type.BOX));
                consumed[i] = true;
                consumed[j] = true;
                break;
            }
        }

        // Underline-style fields: the writable zone is directly above the detected line.
        for (int i = 0; i < merged.size(); i++) {
            if (consumed[i]) continue;
            Segment line = merged.get(i);
            if (!isUsefulLine(line, width, height)) continue;

            float lineWidth = line.width() / (float) width;
            float fieldHeight = lineWidth < 0.18f ? 0.026f : 0.031f;
            float lineY = line.centerY() / height;
            float fx = line.x1 / (float) width;
            float fy = Math.max(0f, lineY - fieldHeight);
            float fw = line.width() / (float) width;
            result.add(new FormField(pageIndex, fx, fy, fw, fieldHeight, FormField.Type.LINE));
        }

        result = deduplicate(result);
        result.sort(Comparator
                .comparingDouble((FormField f) -> f.y)
                .thenComparingDouble(f -> f.x));

        // Avoid flooding the UI on pages containing tables or decorative rules.
        if (result.size() > 80) {
            result = new ArrayList<>(result.subList(0, 80));
        }
        return result;
    }

    private static void addRun(List<Segment> raw, int start, int end, int y, int minRun, int maxRun) {
        if (start < 0 || end < start) return;
        int len = end - start + 1;
        if (len >= minRun && len <= maxRun) raw.add(new Segment(start, end, y));
    }

    private static List<Segment> mergeRows(List<Segment> raw, int width, int height) {
        raw.sort(Comparator.comparingInt((Segment s) -> s.y1).thenComparingInt(s -> s.x1));
        List<Segment> merged = new ArrayList<>();
        int maxVerticalGap = Math.max(2, Math.round(height * 0.0015f));

        for (Segment candidate : raw) {
            Segment best = null;
            float bestOverlap = 0f;
            for (int i = merged.size() - 1; i >= 0; i--) {
                Segment existing = merged.get(i);
                if (candidate.y1 - existing.y2 > maxVerticalGap) break;
                float overlap = overlapRatio(candidate, existing);
                if (overlap > 0.72f && overlap > bestOverlap) {
                    best = existing;
                    bestOverlap = overlap;
                }
            }

            if (best == null) {
                merged.add(new Segment(candidate.x1, candidate.x2, candidate.y1));
            } else {
                best.x1 = Math.min(best.x1, candidate.x1);
                best.x2 = Math.max(best.x2, candidate.x2);
                best.y2 = Math.max(best.y2, candidate.y2);
            }
        }

        int maxThickness = Math.max(8, Math.round(height * 0.008f));
        List<Segment> filtered = new ArrayList<>();
        for (Segment s : merged) {
            if (s.height() <= maxThickness) filtered.add(s);
        }
        return filtered;
    }

    private static boolean isUsefulLine(Segment s, int width, int height) {
        int minWidth = minFieldWidth(width);
        if (s.width() < minWidth) return false;
        if (s.width() > width * 0.90f) return false;
        float cy = s.centerY() / height;
        if (cy < 0.055f || cy > 0.945f) return false;
        if (s.x1 < width * 0.018f || s.x2 > width * 0.982f) return false;
        return true;
    }

    private static int minFieldWidth(int width) {
        return Math.max(48, Math.round(width * 0.075f));
    }

    private static float overlapRatio(Segment a, Segment b) {
        int left = Math.max(a.x1, b.x1);
        int right = Math.min(a.x2, b.x2);
        if (right < left) return 0f;
        int overlap = right - left + 1;
        return overlap / (float) Math.max(1, Math.min(a.width(), b.width()));
    }

    private static List<FormField> deduplicate(List<FormField> fields) {
        List<FormField> result = new ArrayList<>();
        for (FormField candidate : fields) {
            boolean duplicate = false;
            for (FormField existing : result) {
                float dx = Math.abs(candidate.centerX() - existing.centerX());
                float dy = Math.abs(candidate.centerY() - existing.centerY());
                float widthRatio = Math.min(candidate.width, existing.width)
                        / Math.max(candidate.width, existing.width);
                if (dx < 0.018f && dy < 0.012f && widthRatio > 0.70f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(candidate);
        }
        return result;
    }

    private static boolean isLinePixel(int color) {
        if (Color.alpha(color) < 80) return false;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int saturation = max - min;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;

        // Dark rules/underscores plus common blue/teal form lines.
        if (luminance < 145) return true;
        boolean coloredRule = luminance < 225 && saturation > 22 && (b > r + 4 || g > r + 4);
        return coloredRule;
    }
}
