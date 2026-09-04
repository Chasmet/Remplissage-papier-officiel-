package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionHandshakeTest { @Test public void handshakeHasContract() throws Exception {assertEquals("1.0",PrecisionHandshake.json().getString("protocol"));assertTrue(PrecisionHandshake.json().getString("contract_hash").contains("baseline"));} }
