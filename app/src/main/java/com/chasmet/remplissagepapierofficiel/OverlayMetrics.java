package com.chasmet.remplissagepapierofficiel;

import android.graphics.Paint;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesures typographiques et contrôles géométriques utilisés par le pont ChatGPT.
 * Les valeurs de texte sont calculées avec le même moteur Paint Android que le rendu.
 */
public final class OverlayMetrics {
    private OverlayMetrics() {
    }

    public static JSONObject measureText(String text, float textSize) throws Exception {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(Math.max(1f, textSize));
        String safe = text == null ? "" : text;
        Paint.FontMetrics fm = paint.getFontMetrics();

        JSONObject out = new JSONObject();
        out.put("text", safe);
        out.put("font_size", textSize);
        out.put("width", paint.measureText(safe));
        out.put("height", fm.descent - fm.ascent);
        out.put("ascent", fm.ascent);
        out.put("descent", fm.descent);
        out.put("top", fm.top);
        out.put("bottom", fm.bottom);
        out.put("leading", fm.leading);
        out.put("baseline_reference", "exact");
        out.put("font_family", "Android sans-serif default");
        return out;
    }

    public static JSONObject validate(List<TextOverlay> overlays,
                                      int pageWidth, int pageHeight) throws Exception {
        JSONArray warnings = new JSONArray();
        List<Box> boxes = new ArrayList<>();

        if (overlays != null) {
            for (TextOverlay overlay : overlays) {
                if (overlay == null || overlay.text == null || overlay.text.isEmpty()) continue;
                Box box = boxForOverlay(overlay, pageWidth, pageHeight);
                boxes.add(box);

                if (box.left < 0f || box.top < 0f || box.right > pageWidth || box.bottom > pageHeight) {
                    warnings.put(warning(overlay.overlayId, "text_outside_page",
                            "Le texte dépasse la page"));
                }

                if (overlay.width > 0f && box.width() > overlay.width * pageWidth) {
                    warnings.put(warning(overlay.overlayId, "text_too_wide",
                            "Le texte dépasse la largeur déclarée du champ"));
                }

                if (overlay.height > 0f && box.height() > overlay.height * pageHeight) {
                    warnings.put(warning(overlay.overlayId, "font_too_large",
                            "La hauteur du texte dépasse la hauteur déclarée du champ"));
                }

                if (overlay.isCheckbox() && overlay.width > 0f && overlay.height > 0f) {
                    float maxW = overlay.width * pageWidth;
                    float maxH = overlay.height * pageHeight;
                    if (box.width() > maxW || box.height() > maxH) {
                        warnings.put(warning(overlay.overlayId, "checkbox_mark_too_large",
                                "La marque dépasse la zone de la case"));
                    }
                }
            }
        }

        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i);
                Box b = boxes.get(j);
                if (a.pageIndex != b.pageIndex) continue;
                if (RectF.intersects(a.rect(), b.rect())) {
                    JSONObject warning = warning(a.overlayId, "overlay_overlap",
                            "Deux éléments ajoutés se chevauchent");
                    warning.put("other_overlay_id", b.overlayId);
                    warnings.put(warning);
                }
            }
        }

        JSONObject out = new JSONObject();
        out.put("warnings", warnings);
        out.put("warning_count", warnings.length());
        out.put("valid", warnings.length() == 0);
        out.put("page_width", pageWidth);
        out.put("page_height", pageHeight);
        out.put("engine", "android.graphics.Paint");
        return out;
    }

    public static JSONObject geometry(TextOverlay overlay,
                                      int pageWidth, int pageHeight) throws Exception {
        Box box = boxForOverlay(overlay, pageWidth, pageHeight);
        JSONObject out = new JSONObject();
        out.put("overlay_id", overlay.overlayId);
        out.put("page_index", overlay.pageIndex);
        out.put("anchor_x", overlay.x);
        out.put("baseline_y", overlay.y);
        out.put("left_px", box.left);
        out.put("top_px", box.top);
        out.put("right_px", box.right);
        out.put("bottom_px", box.bottom);
        out.put("width_px", box.width());
        out.put("height_px", box.height());
        return out;
    }

    private static Box boxForOverlay(TextOverlay overlay,
                                     int pageWidth, int pageHeight) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(overlay.textSize);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float width = paint.measureText(overlay.text);
        float anchorX = overlay.x * pageWidth;
        float baselineY = overlay.y * pageHeight;

        float left;
        if (TextOverlay.ALIGN_CENTER.equals(overlay.align) || overlay.isCheckbox()) {
            left = anchorX - width / 2f;
        } else if (TextOverlay.ALIGN_RIGHT.equals(overlay.align)) {
            left = anchorX - width;
        } else {
            left = anchorX;
        }

        float top;
        float bottom;
        if (overlay.isCheckbox()) {
            float centerY = baselineY;
            float height = fm.descent - fm.ascent;
            top = centerY - height / 2f;
            bottom = centerY + height / 2f;
        } else {
            top = baselineY + fm.ascent;
            bottom = baselineY + fm.descent;
        }

        return new Box(overlay.overlayId, overlay.pageIndex,
                left, top, left + width, bottom);
    }

    private static JSONObject warning(String overlayId, String problem,
                                      String message) throws Exception {
        JSONObject out = new JSONObject();
        out.put("overlay_id", overlayId);
        out.put("problem", problem);
        out.put("message", message);
        return out;
    }

    private static final class Box {
        final String overlayId;
        final int pageIndex;
        final float left;
        final float top;
        final float right;
        final float bottom;

        Box(String overlayId, int pageIndex, float left, float top,
            float right, float bottom) {
            this.overlayId = overlayId;
            this.pageIndex = pageIndex;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() { return Math.max(0f, right - left); }
        float height() { return Math.max(0f, bottom - top); }
        RectF rect() { return new RectF(left, top, right, bottom); }
    }
}
