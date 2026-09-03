package com.chasmet.remplissagepapierofficiel;

public class FormField {
    public enum Type {
        LINE,
        BOX
    }

    public final int pageIndex;
    public final float x;
    public final float y;
    public final float width;
    public final float height;
    public final Type type;

    public FormField(int pageIndex, float x, float y, float width, float height, Type type) {
        this.pageIndex = pageIndex;
        this.x = clamp01(x);
        this.y = clamp01(y);
        this.width = Math.max(0.001f, Math.min(1f - this.x, width));
        this.height = Math.max(0.001f, Math.min(1f - this.y, height));
        this.type = type == null ? Type.LINE : type;
    }

    public float centerX() {
        return x + width * 0.5f;
    }

    public float centerY() {
        return y + height * 0.5f;
    }

    public float textX() {
        return Math.min(0.99f, x + Math.min(0.012f, width * 0.06f));
    }

    public float textBaselineY() {
        if (type == Type.BOX) {
            return Math.min(0.99f, y + height * 0.70f);
        }
        return Math.min(0.99f, y + height * 0.78f);
    }

    public boolean containsExpanded(float px, float py, float marginX, float marginY) {
        return px >= x - marginX && px <= x + width + marginX
                && py >= y - marginY && py <= y + height + marginY;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
