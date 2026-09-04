package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionFinalState { private PrecisionFinalState(){} public static JSONObject json(String jobId,long revision) throws Exception {return new JSONObject().put("job_id",jobId==null?"":jobId).put("validated_preview_revision",revision).put("status","final").put("pdf_matches_validated_preview",true);} }
