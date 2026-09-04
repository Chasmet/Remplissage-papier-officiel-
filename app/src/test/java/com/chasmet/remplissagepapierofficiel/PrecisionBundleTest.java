package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionBundleTest { @Test public void completeBundle() throws Exception {assertNotNull(PrecisionBundle.json().getJSONObject("bridge"));assertNotNull(PrecisionBundle.json().getJSONObject("security"));} }
