package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionBridgePayload { private PrecisionBridgePayload(){} public static JSONObject json() throws Exception {return new JSONObject().put("precision",PrecisionSummary.json()).put("mcp",PrecisionMcpManifest.json()).put("health",PrecisionHealth.ok()).put("readiness",PrecisionReadiness.json());} }
