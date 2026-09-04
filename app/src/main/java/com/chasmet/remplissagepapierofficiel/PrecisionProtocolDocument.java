package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionProtocolDocument { private PrecisionProtocolDocument(){} public static JSONObject json() throws Exception {return new JSONObject().put("target",PrecisionTarget.text()).put("versions",PrecisionVersionContract.json()).put("diagram",PrecisionLoopDiagram.text()).put("api",PrecisionApiContract.json()).put("verification",PrecisionVerification.json()).put("definition_of_done",PrecisionDoneDefinition.text());} }
