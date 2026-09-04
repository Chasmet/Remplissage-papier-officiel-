package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionLocalEditCapabilitiesTest { @Test public void isolatedEdit() throws Exception {assertTrue(PrecisionLocalEditCapabilities.json().getBoolean("other_overlays_untouched"));} }
