package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionFieldDetection { private PrecisionFieldDetection(){} public static JSONObject json() throws Exception {return new JSONObject().put("detect_lines",true).put("detect_checkboxes",true).put("detect_tables",true).put("detect_multiline",true).put("semantic_hints",true).put("advisory_only",true).put("can_reposition_chatgpt_overlay",false);} }
