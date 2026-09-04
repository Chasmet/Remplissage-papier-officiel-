package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionFeatureFlagsTest { @Test public void automationNeverMovesCoordinates(){ assertFalse(PrecisionFeatureFlags.AUTO_SNAP);assertFalse(PrecisionFeatureFlags.AUTO_REPOSITION);assertTrue(PrecisionFeatureFlags.LOCAL_CORRECTIONS); } }
