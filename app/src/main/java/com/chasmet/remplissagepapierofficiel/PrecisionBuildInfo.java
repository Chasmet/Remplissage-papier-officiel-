package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionBuildInfo { private PrecisionBuildInfo(){} public static JSONObject json() throws Exception {return new JSONObject().put("version_name",BuildConfig.VERSION_NAME).put("version_code",BuildConfig.VERSION_CODE).put("precision_engine",PrecisionVersion.ENGINE).put("minimum_mcp",PrecisionMcpVersion.MINIMUM);} }
