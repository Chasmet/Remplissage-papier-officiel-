package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionReviewOutcome { private PrecisionReviewOutcome(){} public static JSONObject valid(long revision) throws Exception {return new JSONObject().put("status",PrecisionValidationCodes.VALID).put("revision",revision).put("next","export_final_pdf");} public static JSONObject correction(long revision,String id) throws Exception {return new JSONObject().put("status",PrecisionValidationCodes.NEEDS_CORRECTION).put("revision",revision).put("overlay_id",id).put("next","update_overlay");} }
