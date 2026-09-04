package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class CorrectionRequest { private CorrectionRequest(){} public static JSONObject normalized(String id,float dx,float dy) throws Exception {return new JSONObject().put("operation",CorrectionOperationNames.UPDATE).put("overlay_id",id).put("x_delta",dx).put("y_delta",dy).put("units","normalized");} }
