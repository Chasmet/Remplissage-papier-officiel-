package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionReviewCycle { private PrecisionReviewCycle(){} public static JSONObject json(int pass,long revision) throws Exception {return new JSONObject().put("pass",pass).put("revision",revision).put("max_passes",PrecisionLimits.MAX_CORRECTION_PASSES).put("continue_until_validated",true);} }
