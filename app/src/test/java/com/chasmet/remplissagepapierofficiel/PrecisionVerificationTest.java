package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionVerificationTest { @Test public void correctionExampleIncluded() throws Exception {assertNotNull(PrecisionVerification.json().getJSONObject("correction_example"));} }
