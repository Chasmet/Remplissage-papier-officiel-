package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionError {
 private PrecisionError() {}
 public static JSONObject json(String code,String overlayId,String message) throws Exception { return new JSONObject().put("ok",false).put("error",code).put("overlay_id",overlayId==null?"":overlayId).put("message",message==null?"":message).put("coordinates_modified",false); }
}
