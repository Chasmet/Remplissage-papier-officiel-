package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionReleaseGateTest { @Test public void bothRequired() throws Exception {assertFalse(PrecisionReleaseGate.json(true,false).getBoolean("release_ready"));assertTrue(PrecisionReleaseGate.json(true,true).getBoolean("release_ready"));} }
