package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionHandshake { private PrecisionHandshake(){} public static JSONObject json() throws Exception {return new JSONObject().put("protocol",PrecisionProtocolVersion.value()).put("contract_hash",PrecisionContractHash.value()).put("capabilities",PrecisionProtocol.capabilities()).put("tool_descriptions",PrecisionToolDescriptions.json());} }
