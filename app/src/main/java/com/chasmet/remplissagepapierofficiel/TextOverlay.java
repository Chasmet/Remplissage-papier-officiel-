package com.chasmet.remplissagepapierofficiel;

import java.util.Locale;
import java.util.UUID;

/**
 * Un élément posé sur une page PDF. Les coordonnées x/y sont normalisées 0..1.
 * Pour le texte, y représente la baseline exacte. Pour une case, x/y représentent
 * le centre exact. Aucune logique de snap ou de repositionnement n'est appliquée.
 */
public class TextOverlay {
    public static final String ALIGN_LEFT = "left";
    public static final String ALIGN_CENTER = "center";
    public static final String ALIGN_RIGHT = "right";

    public static final String KIND_TEXT = "text";
    public static final String KIND_CHECKBOX = "checkbox";
    public static final String KIND_DATE = "date";
    public static final String KIND_SIGNATURE = "signature";

    public static final String STATE_KNOWN = "known";
    public static final String STATE_UNKNOWN = "unknown";
    public static final String STATE_REQUIRES_USER = "requires_user";
    public static final String STATE_REQUIRES_SIGNATURE = "requires_signature";

    public final String overlayId;
    public final int pageIndex;
    public final float x;
    public final float y;
    public final String text;
    public final float textSize;
    public final String align;
    public final String kind;
    public final float width;
    public final float height;
    public final String dataState;

    public TextOverlay(int pageIndex, float x, float y, String text, float textSize) {
        this(null, pageIndex, x, y, text, textSize,
                ALIGN_LEFT, KIND_TEXT, 0f, 0f, STATE_KNOWN);
    }

    public TextOverlay(int pageIndex, float x, float y, String text, float textSize,
                       String align, String kind, float width, float height) {
        this(null, pageIndex, x, y, text, textSize,
                align, kind, width, height, STATE_KNOWN);
    }

    public TextOverlay(String overlayId, int pageIndex, float x, float y,
                       String text, float textSize, String align, String kind,
                       float width, float height, String dataState) {
        this.overlayId = normalizeId(overlayId);
        this.pageIndex = Math.max(0, pageIndex);
        this.x = requireNormalized(x, "x");
        this.y = requireNormalized(y, "y");
        this.text = text == null ? "" : text;
        this.textSize = Math.max(1f, textSize);
        this.align = normalizeAlign(align);
        this.kind = normalizeKind(kind);
        this.width = optionalNormalized(width);
        this.height = optionalNormalized(height);
        this.dataState = normalizeDataState(dataState);
    }

    public TextOverlay withPosition(float newX, float newY) {
        return new TextOverlay(overlayId, pageIndex, newX, newY, text, textSize,
                align, kind, width, height, dataState);
    }

    public TextOverlay withChanges(Float newX, Float newY, Float newSize,
                                   String newAlign, String newText) {
        return new TextOverlay(
                overlayId,
                pageIndex,
                newX == null ? x : newX,
                newY == null ? y : newY,
                newText == null ? text : newText,
                newSize == null ? textSize : newSize,
                newAlign == null ? align : newAlign,
                kind,
                width,
                height,
                dataState
        );
    }

    public boolean isCheckbox() {
        return KIND_CHECKBOX.equals(kind);
    }

    public boolean isSignature() {
        return KIND_SIGNATURE.equals(kind);
    }

    public static String normalizeAlign(String value) {
        if (ALIGN_CENTER.equalsIgnoreCase(value)) return ALIGN_CENTER;
        if (ALIGN_RIGHT.equalsIgnoreCase(value)) return ALIGN_RIGHT;
        return ALIGN_LEFT;
    }

    public static String normalizeKind(String value) {
        if (value == null) return KIND_TEXT;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (KIND_CHECKBOX.equals(clean) || "check".equals(clean)) return KIND_CHECKBOX;
        if (KIND_DATE.equals(clean)) return KIND_DATE;
        if (KIND_SIGNATURE.equals(clean)) return KIND_SIGNATURE;
        return KIND_TEXT;
    }

    public static String normalizeDataState(String value) {
        if (value == null) return STATE_KNOWN;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (STATE_UNKNOWN.equals(clean)) return STATE_UNKNOWN;
        if (STATE_REQUIRES_USER.equals(clean)) return STATE_REQUIRES_USER;
        if (STATE_REQUIRES_SIGNATURE.equals(clean)) return STATE_REQUIRES_SIGNATURE;
        return STATE_KNOWN;
    }

    private static String normalizeId(String value) {
        String clean = value == null ? "" : value.trim();
        if (!clean.isEmpty()) return clean;
        return "overlay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static float requireNormalized(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(name + " doit être compris entre 0 et 1");
        }
        return value;
    }

    private static float optionalNormalized(float value) {
        if (value <= 0f) return 0f;
        if (Float.isNaN(value) || Float.isInfinite(value) || value > 1f) {
            throw new IllegalArgumentException("width/height doit être compris entre 0 et 1");
        }
        return value;
    }
}
