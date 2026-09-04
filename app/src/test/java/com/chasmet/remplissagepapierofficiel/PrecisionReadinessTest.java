package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionReadinessTest { @Test public void serverWiringStillExplicit() throws Exception {assertTrue(PrecisionReadiness.json().getBoolean("mcp_server_wiring_required"));assertTrue(PrecisionReadiness.json().getBoolean("ci_required"));} }
