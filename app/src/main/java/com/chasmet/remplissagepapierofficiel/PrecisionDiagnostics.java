package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionDiagnostics { private PrecisionDiagnostics(){} public static JSONObject json() throws Exception { return new JSONObject().put("engine",PrecisionVersion.ENGINE).put("render",RenderSemantics.json()).put("preview",PreviewRequirements.json()).put("acceptance",PrecisionAcceptance.json()).put("loop",PrecisionLoopContract.json()); } }
