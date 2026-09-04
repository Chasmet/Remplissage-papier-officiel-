package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionMcpUpgrade { private PrecisionMcpUpgrade(){} public static JSONObject json() throws Exception {return new JSONObject().put("from","4.0.2").put("to",PrecisionMcpVersion.MINIMUM).put("new_tools",PrecisionServerManifest.json().getJSONArray("new_tools")).put("keep_no_auth_main_session",true).put("keep_legacy_tools",true);} }
