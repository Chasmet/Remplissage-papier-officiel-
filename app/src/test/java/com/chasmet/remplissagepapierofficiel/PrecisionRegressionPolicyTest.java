package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionRegressionPolicyTest { @Test public void updatesPreserved() throws Exception {assertTrue(PrecisionRegressionPolicy.json().getBoolean("preserve_permanent_signing"));assertTrue(PrecisionRegressionPolicy.json().getBoolean("preserve_in_app_update"));} }
