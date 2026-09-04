package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionBridgePayloadTest { @Test public void exposesMcpAndHealth() throws Exception {assertNotNull(PrecisionBridgePayload.json().getJSONObject("mcp"));assertTrue(PrecisionBridgePayload.json().getJSONObject("health").getBoolean("ok"));} }
