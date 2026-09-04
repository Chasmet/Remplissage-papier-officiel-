package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import java.util.List;

/** Final export is allowed only after the current overlay set has been validated. */
public final class FinalExportGate {
    private FinalExportGate() {}

    public static JSONObject evaluate(List<TextOverlay> overlays, boolean chatGptValidatedCurrentPreview) throws Exception {
        JSONObject quality=OverlayQualityReport.build(overlays);
        boolean allowed=chatGptValidatedCurrentPreview && quality.getBoolean("ok");
        return new JSONObject()
                .put("export_allowed",allowed)
                .put("chatgpt_preview_validated",chatGptValidatedCurrentPreview)
                .put("quality",quality);
    }
}
