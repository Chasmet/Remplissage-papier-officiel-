package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionHealthTest { @Test public void reportsCriticalFeatures() throws Exception {assertTrue(PrecisionHealth.ok().getBoolean("strict_coordinates"));assertTrue(PrecisionHealth.ok().getBoolean("local_corrections"));} }
