package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;

/** Identifies every visual pass so ChatGPT never validates a stale preview. */
public final class PreviewRevision {
    private PreviewRevision() {}

    public static JSONObject create(String jobId,String commandId,long revision,String overlayId) throws Exception {
        return new JSONObject()
                .put("job_id",jobId==null?"":jobId)
                .put("command_id",commandId==null?"":commandId)
                .put("preview_revision",revision)
                .put("modified_overlay_id",overlayId==null?"":overlayId)
                .put("fresh",true);
    }
}
