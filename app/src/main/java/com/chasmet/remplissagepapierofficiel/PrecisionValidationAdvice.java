package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionValidationAdvice { private PrecisionValidationAdvice(){} public static JSONObject json(String overlayId,String problem) throws Exception {return new JSONObject().put("overlay_id",overlayId).put("problem",problem).put("suggest_correction",true).put("apply_correction_automatically",false).put("decision_owner","ChatGPT");} }
