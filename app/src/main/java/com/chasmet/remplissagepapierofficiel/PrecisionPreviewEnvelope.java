package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionPreviewEnvelope { private PrecisionPreviewEnvelope(){} public static JSONObject json(String jobId,int page,long revision,int width,int height) throws Exception {return new JSONObject().put("job_id",jobId==null?"":jobId).put("page_index",page).put("preview_revision",revision).put("geometry",PreviewCoordinateMetadata.json(width,height)).put("pixel_matches_current_overlay_state",true);} }
