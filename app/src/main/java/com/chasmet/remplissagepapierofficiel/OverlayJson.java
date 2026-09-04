package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;

public final class OverlayJson {
    private OverlayJson() {}
    public static JSONObject toJson(TextOverlay o) throws Exception {
        return new JSONObject().put("overlay_id",o.overlayId).put("page_index",o.pageIndex)
                .put("x",o.x).put("y",o.y).put("text",o.text).put("size",o.textSize)
                .put("align",o.align).put("kind",o.kind).put("width",o.width).put("height",o.height)
                .put("data_state",o.dataState)
                .put("y_reference",o.isCheckbox()?"center":"baseline")
                .put("coordinates_authoritative",true);
    }
}
