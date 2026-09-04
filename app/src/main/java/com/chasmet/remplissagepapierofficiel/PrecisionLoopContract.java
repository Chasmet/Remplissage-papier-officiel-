package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;

/** End-to-end machine-readable workflow expected by ChatGPT and Android. */
public final class PrecisionLoopContract {
    private PrecisionLoopContract() {}
    public static JSONObject json() throws Exception {
        JSONArray flow=new JSONArray();
        flow.put("pdf_original").put("page_image").put("chatgpt_visual_analysis")
                .put("overlay_commands").put("android_render")
                .put("full_preview").put("modified_overlay_crops")
                .put("chatgpt_local_corrections").put("layout_validation")
                .put("chatgpt_validation").put("final_pdf");
        return new JSONObject().put("flow",flow)
                .put("corrections_are_incremental",true)
                .put("full_plan_required_for_correction",false)
                .put("preview_after_every_change",true)
                .put("final_export_requires_current_preview_validation",true);
    }
}
