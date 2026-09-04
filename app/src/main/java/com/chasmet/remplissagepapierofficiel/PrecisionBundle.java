package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionBundle { private PrecisionBundle(){} public static JSONObject json() throws Exception {return new JSONObject().put("bridge",PrecisionBridgePayload.json()).put("compatibility",PrecisionCompatibility.json()).put("security",PrecisionSecurity.json()).put("diagnostics",PrecisionDiagnostics.json());} }
