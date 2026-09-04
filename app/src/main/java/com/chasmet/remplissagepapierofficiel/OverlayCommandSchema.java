package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class OverlayCommandSchema { private OverlayCommandSchema(){} public static JSONObject update() throws Exception { return new JSONObject().put("operation","update_overlay").put("required","overlay_id").put("absolute","x,y,size,align,text").put("relative","x_delta,y_delta").put("delta_units","normalized_page_fraction").put("replaces_full_plan",false); } }
