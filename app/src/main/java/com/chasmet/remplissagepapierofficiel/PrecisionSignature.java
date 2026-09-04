package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionSignature { private PrecisionSignature(){} public static JSONObject json() throws Exception {return new JSONObject().put("kind","signature").put("touch_input",true).put("image_input",true).put("leave_blank",true).put("chatgpt_auto_signature",false);} }
