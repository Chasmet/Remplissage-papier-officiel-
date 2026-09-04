package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionNoGuessPolicy { private PrecisionNoGuessPolicy(){} public static JSONObject json() throws Exception {return new JSONObject().put("invent_missing_values",false).put("invent_signature",false).put("unknown_state","unknown").put("ask_user_state","requires_user").put("signature_state","requires_signature");} }
