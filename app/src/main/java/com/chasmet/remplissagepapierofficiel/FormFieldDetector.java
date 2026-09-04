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
        if (width < 120 || height < 120) return Collections.emptyList();

        final int left = Math.max(2, Math.round(width * 0.02f));
        final int right = Math.min(width - 3, Math.round(width * 0.98f));
        final int top = Math.max(2, Math.round(height * 0.04f));
        final int bottom = Math.min(height - 3, Math.round(height * 0.96f));
        final int minRun = Math.max(42, Math.round(width * 0.055f));
        final int maxRun = Math.round(width * 0.93f);
        final int maxGap = Math.max(1, Math.round(width / 1000f));

        List<Segment> raw = new ArrayList<>();
        int[] row = new int[width];

        for (int y = top; y <= bottom; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            int runStart = -1;
            int lastInk = -1;
            int gap = 0;

            for (int x = left; x <= right; x++) {
                if (isRulePixel(row[x])) {
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

        List<Segment> merged = mergeRows(raw, height);
        List<FormField> result = new ArrayList<>();
        boolean[] consumed = new boolean[merged.size()];

        detectCheckboxes(bitmap, pageIndex, result);
        detectBoxes(bitmap, pageIndex, merged, consumed, result);
        detectUnderlines(bitmap, pageIndex, merged, consumed, result);

        result = deduplicate(result);
        result.sort(Comparator
                .comparingDouble((FormField f) -> f.y)
                .thenComparingDouble(f -> f.x));

        if (result.size() > 160) {
            result.sort((a, b) -> Float.compare(b.confidence, a.confidence));
            result = new ArrayList<>(result.subList(0, 160));
            result.sort(Comparator
                    .comparingDouble((FormField f) -> f.y)
                    .thenComparingDouble(f -> f.x));
        }
        return result;
    }

    private static void detectCheckboxes(Bitmap bitmap, int pageIndex, List<FormField> out) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int left = Math.max(2, Math.round(width * 0.02f));
        final int right = Math.min(width - 3, Math.round(width * 0.98f));
        final int top = Math.max(2, Math.round(height * 0.04f));
        final int bottom = Math.min(height - 3, Math.round(height * 0.96f));
        final int minRun = Math.max(7, Math.round(width * 0.006f));
        final int maxRun = Math.max(minRun + 4, Math.round(width * 0.035f));
        final int maxGap = Math.max(1, Math.round(width / 1400f));

        List<Segment> raw = new ArrayList<>();
        int[] row = new int[width];

        for (int y = top; y <= bottom; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            int runStart = -1;
            int lastInk = -1;
            int gap = 0;

            for (int x = left; x <= right; x++) {
                if (isRulePixel(row[x])) {
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

        if (raw.isEmpty()) return;
        List<Segment> merged = mergeRows(raw, height);

        for (int i = 0; i < merged.size(); i++) {
            Segment topLine = merged.get(i);
            float nw = topLine.width() / (float) width;
            if (nw < 0.006f || nw > 0.035f) continue;

            for (int j = i + 1; j < merged.size(); j++) {
                Segment bottomLine = merged.get(j);
                float dy = bottomLine.centerY() - topLine.centerY();
                if (dy > height * 0.040f) break;
                if (dy < height * 0.005f) continue;

                float bottomWidth = bottomLine.width() / (float) width;
                if (bottomWidth < 0.006f || bottomWidth > 0.035f) continue;

                float overlap = overlapRatio(topLine, bottomLine);
                float widthRatio = Math.min(topLine.width(), bottomLine.width())
                        / (float) Math.max(topLine.width(), bottomLine.width());
                if (overlap < 0.72f || widthRatio < 0.68f) continue;

                int x1 = Math.max(topLine.x1, bottomLine.x1);
                int x2 = Math.min(topLine.x2, bottomLine.x2);
                int y1 = topLine.y2 + 1;
                int y2 = bottomLine.y1 - 1;
                if (x2 <= x1 || y2 <= y1) continue;

                float boxW = (x2 - x1 + 1) / (float) width;
                float boxH = (y2 - y1 + 1) / (float) height;
                if (boxW < 0.005f || boxW > 0.034f || boxH < 0.004f || boxH > 0.034f) continue;

                float aspect = (boxW * width) / Math.max(1f, boxH * height);
                if (aspect < 0.55f || aspect > 1.70f) continue;

                float leftSide = verticalBorderEvidence(bitmap, x1, y1, y2);
                float rightSide = verticalBorderEvidence(bitmap, x2, y1, y2);
                float sideEvidence = (leftSide + rightSide) * 0.5f;
                if (sideEvidence < 0.40f) continue;

                int padX = Math.max(6, Math.round(width * 0.08f));
                int padY = Math.max(5, Math.round(height * 0.012f));
                float rightContext = inkDensity(bitmap,
                        Math.min(width - 1, x2 + 2),
                        Math.max(0, y1 - padY),
                        Math.min(width - 1, x2 + padX),
                        Math.min(height - 1, y2 + padY));
                float leftContext = inkDensity(bitmap,
                        Math.max(0, x1 - padX),
                        Math.max(0, y1 - padY),
                        Math.max(0, x1 - 2),
                        Math.min(height - 1, y2 + padY));
                float context = Math.max(rightContext, leftContext);
                if (context < 0.002f) continue;

                float interior = inkDensity(bitmap,
                        x1 + Math.max(1, (x2 - x1) / 5),
                        y1 + Math.max(1, (y2 - y1) / 5),
                        x2 - Math.max(1, (x2 - x1) / 5),
                        y2 - Math.max(1, (y2 - y1) / 5));

                float confidence = 0.62f
                        + Math.min(0.20f, sideEvidence * 0.22f)
                        + Math.min(0.10f, context * 2.0f);
                if (interior < 0.12f) confidence += 0.06f;
                confidence = clamp01(confidence);
                if (confidence < 0.68f) continue;

                out.add(new FormField(
                        pageIndex,
                        x1 / (float) width,
                        y1 / (float) height,
                        boxW,
                        boxH,
                        FormField.Type.CHECKBOX,
                        confidence
                ));
                break;
            }
        }
    }

    private static void detectBoxes(Bitmap bitmap, int pageIndex, List<Segment> merged,
                                    boolean[] consumed, List<FormField> out) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int minFieldWidth = Math.max(40, Math.round(width * 0.055f));

        for (int i = 0; i < merged.size(); i++) {
            Segment topLine = merged.get(i);
            if (!isUsefulHorizontal(topLine, width, height)) continue;

            for (int j = i + 1; j < merged.size(); j++) {
                Segment bottomLine = merged.get(j);
                float dy = bottomLine.centerY() - topLine.centerY();
                if (dy > height * 0.058f) break;
                if (dy < height * 0.010f) continue;
                if (!isUsefulHorizontal(bottomLine, width, height)) continue;

                float overlap = overlapRatio(topLine, bottomLine);
                float widthRatio = Math.min(topLine.width(), bottomLine.width())
                        / (float) Math.max(topLine.width(), bottomLine.width());
                if (overlap < 0.91f || widthRatio < 0.88f) continue;

                int x1 = Math.max(topLine.x1, bottomLine.x1);
                int x2 = Math.min(topLine.x2, bottomLine.x2);
                int y1 = topLine.y2 + 1;
                int y2 = bottomLine.y1 - 1;
                if (x2 - x1 + 1 < minFieldWidth || y2 <= y1) continue;

                float normalizedWidth = (x2 - x1 + 1) / (float) width;
                float normalizedHeight = (y2 - y1 + 1) / (float) height;
                if (normalizedHeight > 0.052f || normalizedHeight < 0.009f) continue;
                if (normalizedWidth > 0.86f) continue;

                float leftSide = verticalBorderEvidence(bitmap, x1, y1, y2);
                float rightSide = verticalBorderEvidence(bitmap, x2, y1, y2);
                float sideEvidence = (leftSide + rightSide) * 0.5f;

                float interiorInk = inkDensity(bitmap,
                        x1 + Math.max(2, (x2 - x1) / 45),
                        y1 + 1,
                        x2 - Math.max(2, (x2 - x1) / 45),
                        y2 - 1);

                // Headings and section bands usually contain much more text and have no side borders.
                if (sideEvidence < 0.16f && interiorInk > 0.055f) continue;
                if (sideEvidence < 0.10f) continue;
                if (interiorInk > 0.19f) continue;

                float confidence = 0.46f;
                confidence += Math.min(0.27f, sideEvidence * 0.42f);
                confidence += Math.max(0f, 0.14f - interiorInk * 0.55f);
                if (normalizedHeight >= 0.014f && normalizedHeight <= 0.040f) confidence += 0.09f;
                if (normalizedWidth <= 0.66f) confidence += 0.05f;
                confidence = clamp01(confidence);
                if (confidence < 0.58f) continue;

                out.add(new FormField(
                        pageIndex,
                        x1 / (float) width,
                        y1 / (float) height,
                        (x2 - x1 + 1) / (float) width,
                        Math.max(0.012f, (y2 - y1 + 1) / (float) height),
                        FormField.Type.BOX,
                        confidence
                ));
                consumed[i] = true;
                consumed[j] = true;
                break;
            }
        }
    }

    private static void detectUnderlines(Bitmap bitmap, int pageIndex, List<Segment> merged,
                                         boolean[] consumed, List<FormField> out) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        for (int i = 0; i < merged.size(); i++) {
            if (consumed[i]) continue;
            Segment line = merged.get(i);
            if (!isUsefulHorizontal(line, width, height)) continue;

            float normalizedWidth = line.width() / (float) width;
            if (normalizedWidth > 0.60f) continue;

            float fieldHeight = normalizedWidth < 0.16f ? 0.025f : 0.031f;
            int bandHeight = Math.max(8, Math.round(height * fieldHeight));
            int bandTop = Math.max(0, line.y1 - bandHeight);
            int bandBottom = Math.max(bandTop, line.y1 - 1);

            float insideInk = inkDensity(bitmap, line.x1, bandTop, line.x2, bandBottom);
            if (insideInk > 0.18f) continue;

            int contextPadX = Math.max(12, Math.round(width * 0.11f));
            int contextPadY = Math.max(8, Math.round(height * 0.024f));
            float leftInk = inkDensity(bitmap,
                    Math.max(0, line.x1 - contextPadX),
                    Math.max(0, line.y1 - contextPadY),
                    Math.max(0, line.x1 - 2),
                    Math.min(height - 1, line.y2 + 2));
            float aboveInk = inkDensity(bitmap,
                    line.x1,
                    Math.max(0, line.y1 - contextPadY * 2),
                    line.x2,
                    Math.max(0, line.y1 - contextPadY));
            float contextInk = Math.max(leftInk, aboveInk);

            // Very long isolated rules are usually separators, not writable lines.
            if (normalizedWidth > 0.42f && contextInk < 0.008f) continue;

            float confidence = 0.50f;
            confidence += Math.max(0f, 0.16f - insideInk * 0.55f);
            confidence += Math.min(0.15f, contextInk * 2.4f);
            if (normalizedWidth >= 0.08f && normalizedWidth <= 0.42f) confidence += 0.08f;
            if (line.height() <= Math.max(5, Math.round(height * 0.004f))) confidence += 0.05f;
            confidence = clamp01(confidence);
            if (confidence < 0.59f) continue;

            float lineY = line.centerY() / height;
            out.add(new FormField(
                    pageIndex,
                    line.x1 / (float) width,
                    Math.max(0f, lineY - fieldHeight),
                    line.width() / (float) width,
                    fieldHeight,
                    FormField.Type.LINE,
                    confidence
            ));
        }
    }

    private static float verticalBorderEvidence(Bitmap bitmap, int x, int y1, int y2) {
        if (y2 <= y1) return 0f;
        int width = bitmap.getWidth();
        int radius = Math.max(1, width / 900);
        int samples = 0;
        int hits = 0;
        int step = Math.max(1, (y2 - y1 + 1) / 35);

        for (int y = y1; y <= y2; y += step) {
            samples++;
            boolean hit = false;
            for (int dx = -radius; dx <= radius; dx++) {
                int sx = Math.max(0, Math.min(width - 1, x + dx));
                if (isRulePixel(bitmap.getPixel(sx, y))) {
                    hit = true;
                    break;
                }
            }
            if (hit) hits++;
        }
        return samples == 0 ? 0f : hits / (float) samples;
    }

    private static float inkDensity(Bitmap bitmap, int x1, int y1, int x2, int y2) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        x1 = Math.max(0, Math.min(width - 1, x1));
        x2 = Math.max(0, Math.min(width - 1, x2));
        y1 = Math.max(0, Math.min(height - 1, y1));
        y2 = Math.max(0, Math.min(height - 1, y2));
        if (x2 < x1 || y2 < y1) return 0f;

        int stepX = Math.max(1, (x2 - x1 + 1) / 90);
        int stepY = Math.max(1, (y2 - y1 + 1) / 32);
        int samples = 0;
        int ink = 0;

        for (int y = y1; y <= y2; y += stepY) {
            for (int x = x1; x <= x2; x += stepX) {
                samples++;
                if (isTextPixel(bitmap.getPixel(x, y))) ink++;
            }
        }
        return samples == 0 ? 0f : ink / (float) samples;
    }

    private static void addRun(List<Segment> raw, int start, int end, int y, int minRun, int maxRun) {
        if (start < 0 || end < start) return;
        int len = end - start + 1;
        if (len >= minRun && len <= maxRun) raw.add(new Segment(start, end, y));
    }

    private static List<Segment> mergeRows(List<Segment> raw, int height) {
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
                if (overlap > 0.74f && overlap > bestOverlap) {
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

        int maxThickness = Math.max(8, Math.round(height * 0.007f));
        List<Segment> filtered = new ArrayList<>();
        for (Segment s : merged) {
            if (s.height() <= maxThickness) filtered.add(s);
        }
        return filtered;
    }

    private static boolean isUsefulHorizontal(Segment s, int width, int height) {
        int minWidth = Math.max(40, Math.round(width * 0.055f));
        if (s.width() < minWidth) return false;
        if (s.width() > width * 0.90f) return false;
        float cy = s.centerY() / height;
        if (cy < 0.05f || cy > 0.95f) return false;
        if (s.x1 < width * 0.012f || s.x2 > width * 0.988f) return false;
        return true;
    }

    private static float overlapRatio(Segment a, Segment b) {
        int left = Math.max(a.x1, b.x1);
        int right = Math.min(a.x2, b.x2);
        if (right < left) return 0f;
        int overlap = right - left + 1;
        return overlap / (float) Math.max(1, Math.min(a.width(), b.width()));
    }

    private static List<FormField> deduplicate(List<FormField> fields) {
        fields.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        List<FormField> result = new ArrayList<>();
        for (FormField candidate : fields) {
            boolean duplicate = false;
            for (FormField existing : result) {
                float dx = Math.abs(candidate.centerX() - existing.centerX());
                float dy = Math.abs(candidate.centerY() - existing.centerY());
                float widthRatio = Math.min(candidate.width, existing.width)
                        / Math.max(candidate.width, existing.width);
                if (dx < 0.020f && dy < 0.014f && widthRatio > 0.64f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(candidate);
        }
        return result;
    }

    private static boolean isRulePixel(int color) {
        if (Color.alpha(color) < 80) return false;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int saturation = max - min;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;

        if (luminance < 150) return true;
        return luminance < 224 && saturation > 20 && (b > r + 3 || g > r + 3);
    }

    private static boolean isTextPixel(int color) {
        if (Color.alpha(color) < 70) return false;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        return luminance < 135;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
