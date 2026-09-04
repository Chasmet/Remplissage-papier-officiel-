package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCompatibility { private PrecisionCompatibility(){} public static JSONObject json() throws Exception {return new JSONObject().put("paper_open_active_document",true).put("paper_get_page_image",true).put("paper_get_preview_image",true).put("paper_submit_fill_plan",true).put("paper_get_filled_document",true).put("precision_tools_additive",true);} }
