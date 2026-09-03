package com.chasmet.remplissagepapierofficiel;

public class TextOverlay {
    public final int pageIndex;
    public final float x;
    public final float y;
    public final String text;
    public final float textSize;

    public TextOverlay(int pageIndex, float x, float y, String text, float textSize) {
        this.pageIndex = pageIndex;
        this.x = x;
        this.y = y;
        this.text = text;
        this.textSize = textSize;
    }
}
