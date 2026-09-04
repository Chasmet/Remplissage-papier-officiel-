package com.chasmet.remplissagepapierofficiel;

public class TextOverlay {
    public static final String ALIGN_LEFT = "left";
    public static final String ALIGN_CENTER = "center";
    public static final String ALIGN_RIGHT = "right";

    public static final String KIND_TEXT = "text";
    public static final String KIND_CHECKBOX = "checkbox";

    public final int pageIndex;
    public final float x;
    public final float y;
    public final String text;
    public final float textSize;
    public final String align;
    public final String kind;
    public final float width;
    public final float height;

    public TextOverlay(int pageIndex, float x, float y, String text, float textSize) {
        this(pageIndex, x, y, text, textSize, ALIGN_LEFT, KIND_TEXT, 0f, 0f);
    }

    public TextOverlay(int pageIndex, float x, float y, String text, float textSize,
                       String align, String kind, float width, float height) {
        this.pageIndex = pageIndex;
        this.x = clamp01(x);
        this.y = clamp01(y);
        this.text = text == null ? "" : text;
        this.textSize = Math.max(1f, textSize);
        this.align = normalizeAlign(align);
        this.kind = normalizeKind(kind);
        this.width = clamp01(width);
        this.height = clamp01(height);
    }

    public boolean isCheckbox() {
        return KIND_CHECKBOX.equals(kind);
    }

    public static String normalizeAlign(String value) {
        if (ALIGN_CENTER.equalsIgnoreCase(value)) return ALIGN_CENTER;
        if (ALIGN_RIGHT.equalsIgnoreCase(value)) return ALIGN_RIGHT;
        return ALIGN_LEFT;
    }

    public static String normalizeKind(String value) {
        if (KIND_CHECKBOX.equalsIgnoreCase(value) || "check".equalsIgnoreCase(value)) {
            return KIND_CHECKBOX;
        }
        return KIND_TEXT;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
