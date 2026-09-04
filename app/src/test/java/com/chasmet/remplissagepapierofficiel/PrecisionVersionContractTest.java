package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionVersionContractTest { @Test public void versions() throws Exception {assertEquals("1.10.0",PrecisionVersionContract.json().getString("android"));assertEquals("4.1.0",PrecisionVersionContract.json().getString("mcp_minimum"));} }
