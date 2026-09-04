package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionValidationResult { private PrecisionValidationResult(){} public static JSONObject accepted(long revision) throws Exception {return new JSONObject().put("accepted",true).put("preview_revision",revision).put("final_pdf_allowed",true);} public static JSONObject correctionNeeded(long revision,String overlayId) throws Exception {return new JSONObject().put("accepted",false).put("preview_revision",revision).put("overlay_id",overlayId).put("final_pdf_allowed",false);} }
