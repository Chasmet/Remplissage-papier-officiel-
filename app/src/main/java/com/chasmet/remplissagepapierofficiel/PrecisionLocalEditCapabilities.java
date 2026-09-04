package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionLocalEditCapabilities { private PrecisionLocalEditCapabilities(){} public static JSONObject json() throws Exception {return new JSONObject().put("move_x",true).put("move_y",true).put("delta_x",true).put("delta_y",true).put("resize_font",true).put("change_alignment",true).put("replace_text",true).put("delete",true).put("other_overlays_untouched",true);} }
