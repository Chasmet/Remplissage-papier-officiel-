package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Lightweight deterministic pre-export validation report. */
public final class OverlayQualityReport {
    private OverlayQualityReport() {}

    public static JSONObject build(List<TextOverlay> overlays) throws Exception {
        JSONArray warnings = new JSONArray();
        if (overlays != null) {
            for (TextOverlay o : overlays) {
                if (o == null) continue;
                if (o.textSize > 48f) warn(warnings,o,"font_too_large");
                if (TextOverlay.STATE_UNKNOWN.equals(o.dataState)) warn(warnings,o,"unknown_value");
                if (o.isSignature() && o.text != null && !o.text.isEmpty()) warn(warnings,o,"signature_requires_user_input");
                if (o.width > 0f) {
                    float left = o.x;
                    if (TextOverlay.ALIGN_CENTER.equals(o.align)) left -= o.width / 2f;
                    else if (TextOverlay.ALIGN_RIGHT.equals(o.align)) left -= o.width;
                    if (left < 0f || left + o.width > 1f) warn(warnings,o,"declared_width_outside_page");
                }
            }
            for (int i=0;i<overlays.size();i++) for (int j=i+1;j<overlays.size();j++) {
                TextOverlay a=overlays.get(i), b=overlays.get(j);
                if (a==null||b==null||a.pageIndex!=b.pageIndex||a.width<=0||a.height<=0||b.width<=0||b.height<=0) continue;
                if (Math.abs(a.x-b.x) < (a.width+b.width)/2f && Math.abs(a.y-b.y) < (a.height+b.height)/2f)
                    warn(warnings,a,"overlaps_overlay:"+b.overlayId);
            }
        }
        return new JSONObject().put("ok",warnings.length()==0).put("warnings",warnings);
    }

    private static void warn(JSONArray out, TextOverlay o, String problem) throws Exception {
        out.put(new JSONObject().put("overlay_id",o.overlayId).put("page_index",o.pageIndex).put("problem",problem));
    }
}
