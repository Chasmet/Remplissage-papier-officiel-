package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionServerHandshakeTest { @Test public void noTokenRequired() throws Exception {assertTrue(PrecisionServerHandshake.json().getBoolean("token_optional"));} }
