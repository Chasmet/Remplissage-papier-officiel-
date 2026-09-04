package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionSessionPolicyTest { @Test public void mainSession(){assertTrue(PrecisionSessionPolicy.TOKEN_OPTIONAL);assertEquals("Android principal",PrecisionSessionPolicy.DEFAULT_SESSION_LABEL);} }
