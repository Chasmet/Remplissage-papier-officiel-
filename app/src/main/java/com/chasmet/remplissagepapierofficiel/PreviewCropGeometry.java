package com.chasmet.remplissagepapierofficiel;

import android.graphics.Rect;

/** Geometry helper for high-resolution crops around a modified overlay. */
public final class PreviewCropGeometry {
    private PreviewCropGeometry() {}

    public static Rect around(TextOverlay overlay, int pageWidth, int pageHeight,
                              float horizontalMargin, float verticalMargin) {
        if (overlay == null) throw new IllegalArgumentException("overlay requis");
        int cx = Math.round(overlay.x * pageWidth);
        int cy = Math.round(overlay.y * pageHeight);
        int halfW = Math.max(80, Math.round(Math.max(overlay.width, 0.18f) * pageWidth * 0.5f));
        int halfH = Math.max(50, Math.round(Math.max(overlay.height, 0.04f) * pageHeight * 0.5f));
        halfW += Math.round(Math.max(0f, horizontalMargin) * pageWidth);
        halfH += Math.round(Math.max(0f, verticalMargin) * pageHeight);
        return new Rect(
                Math.max(0, cx - halfW),
                Math.max(0, cy - halfH),
                Math.min(pageWidth, cx + halfW),
                Math.min(pageHeight, cy + halfH));
    }
}
