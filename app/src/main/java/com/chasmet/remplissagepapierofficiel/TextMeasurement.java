package com.chasmet.remplissagepapierofficiel;

import android.graphics.Paint;
import org.json.JSONObject;

/** Measures text with the same Android Paint metrics used by preview rendering. */
public final class TextMeasurement {
    private TextMeasurement() {}

    public static JSONObject measure(String text, float fontSize) throws Exception {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTextSize(Math.max(1f,fontSize));
        String value=text==null?"":text;
        Paint.FontMetrics fm=p.getFontMetrics();
        return new JSONObject()
                .put("text",value)
                .put("font_size",p.getTextSize())
                .put("width",p.measureText(value))
                .put("height",fm.descent-fm.ascent)
                .put("ascent",fm.ascent)
                .put("descent",fm.descent)
                .put("leading",fm.leading)
                .put("baseline_reference","exact");
    }
}
