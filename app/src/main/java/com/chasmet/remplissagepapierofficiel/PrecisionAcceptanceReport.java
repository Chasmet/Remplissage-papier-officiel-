package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionAcceptanceReport { private PrecisionAcceptanceReport(){} public static JSONObject json() throws Exception {return new JSONObject().put("version",PrecisionVersion.ENGINE).put("criteria",PrecisionAcceptance.json()).put("server_requirements",PrecisionServerRequirements.json()).put("completion",PrecisionCompletionCriteria.json());} }
