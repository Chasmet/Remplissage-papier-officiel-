package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionHealth { private PrecisionHealth(){} public static JSONObject ok() throws Exception {return new JSONObject().put("ok",true).put("engine",PrecisionVersion.ENGINE).put("strict_coordinates",PrecisionFeatureFlags.STRICT_COORDINATES).put("local_corrections",PrecisionFeatureFlags.LOCAL_CORRECTIONS).put("automatic_preview",PrecisionFeatureFlags.AUTOMATIC_PREVIEW);} }
