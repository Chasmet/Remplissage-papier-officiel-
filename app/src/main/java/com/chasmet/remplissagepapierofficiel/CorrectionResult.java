package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;

/** Response returned after an atomic correction so the MCP can request a fresh preview immediately. */
public final class CorrectionResult {
    private CorrectionResult() {}

    public static JSONObject success(String overlayId) throws Exception {
        return new JSONObject()
                .put("ok",true)
                .put("overlay_id",overlayId)
                .put("preview_required",true)
                .put("crop_required",true)
                .put("rebuild_document",false);
    }
}
