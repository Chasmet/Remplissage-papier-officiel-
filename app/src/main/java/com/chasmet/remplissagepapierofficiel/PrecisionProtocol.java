package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;

/** Machine-readable capabilities advertised to the MCP bridge. */
public final class PrecisionProtocol {
    private PrecisionProtocol() {}

    public static JSONObject capabilities() throws Exception {
        return new JSONObject()
                .put("protocol","remplissage-papier-officiel.precision.v1")
                .put("coordinates","normalized-0-to-1-strict")
                .put("text_y_reference","baseline")
                .put("checkbox_xy_reference","center")
                .put("coordinates_authoritative",true)
                .put("snapping",false)
                .put("auto_fit",false)
                .put("overlay_ids",true)
                .put("local_update",true)
                .put("local_delete",true)
                .put("text_measurement",true)
                .put("preview_crops",true)
                .put("layout_validation",true)
                .put("kinds",new JSONArray().put("text").put("checkbox").put("date").put("signature"))
                .put("data_states",new JSONArray().put("known").put("unknown").put("requires_user").put("requires_signature"));
    }
}
