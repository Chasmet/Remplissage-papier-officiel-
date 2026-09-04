package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionApiContract { private PrecisionApiContract(){} public static JSONObject json() throws Exception {return new JSONObject().put("handshake",PrecisionHandshake.json()).put("server",PrecisionServerManifest.json()).put("actions",PrecisionServerActionMap.json()).put("contracts",PrecisionAllContracts.json()).put("local_edits",PrecisionLocalEditCapabilities.json()).put("release",PrecisionRegressionPolicy.json());} }
