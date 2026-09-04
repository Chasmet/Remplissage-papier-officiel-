package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PreviewValidationToken { private PreviewValidationToken(){} public static JSONObject create(String jobId,long revision) throws Exception {return new JSONObject().put("job_id",jobId==null?"":jobId).put("preview_revision",revision).put("must_match_current_revision",true);} }
