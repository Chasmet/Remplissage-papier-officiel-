package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionSecurity { private PrecisionSecurity(){} public static JSONObject json() throws Exception {return new JSONObject().put("mcp_token_optional",true).put("signature_generation_by_chatgpt",false).put("coordinates_only_for_active_document",true).put("external_phone_control",false);} }
