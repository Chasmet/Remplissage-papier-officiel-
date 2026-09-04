package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionRuntimeInfo { private PrecisionRuntimeInfo(){} public static JSONObject json() throws Exception {return new JSONObject().put("app_version",BuildConfig.VERSION_NAME).put("precision_engine",PrecisionVersion.ENGINE).put("protocol",PrecisionProtocolVersion.value()).put("contracts",PrecisionAllContracts.json());} }
