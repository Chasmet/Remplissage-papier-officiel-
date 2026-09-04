package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionServerHandshake { private PrecisionServerHandshake(){} public static JSONObject json() throws Exception {return new JSONObject().put("minimum_server_version",PrecisionMcpVersion.MINIMUM).put("precision_protocol",PrecisionProtocolVersion.value()).put("contract_hash",PrecisionContractHash.value()).put("token_optional",true);} }
