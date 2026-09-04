package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCorrectionEnvelope { private PrecisionCorrectionEnvelope(){} public static JSONObject json(String jobId,long expectedRevision,JSONObject operation) throws Exception {return new JSONObject().put("job_id",jobId).put("expected_preview_revision",expectedRevision).put("operation",operation).put("reject_if_revision_changed",true);} }
