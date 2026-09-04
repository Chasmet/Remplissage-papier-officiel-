package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionRuntimeInfoTest { @Test public void protocolExposed() throws Exception {assertEquals("1.0",PrecisionRuntimeInfo.json().getString("protocol"));} }
