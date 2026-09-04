package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class OverlayValidationWarning { private OverlayValidationWarning(){} public static JSONObject json(String overlayId,String problem,String severity) throws Exception { return new JSONObject().put("overlay_id",overlayId==null?"":overlayId).put("problem",problem==null?"":problem).put("severity",severity==null?"warning":severity).put("advisory",true); } }
