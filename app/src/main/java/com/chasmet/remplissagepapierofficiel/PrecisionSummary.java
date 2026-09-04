package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionSummary { private PrecisionSummary(){} public static JSONObject json() throws Exception {return new JSONObject().put("version",PrecisionVersion.ENGINE).put("critical_trio","exact_coordinates + local_correction + automatic_preview").put("coordinates",RenderSemantics.json()).put("update_schema",OverlayCommandSchema.update()).put("preview",PreviewRequirements.json());} }
