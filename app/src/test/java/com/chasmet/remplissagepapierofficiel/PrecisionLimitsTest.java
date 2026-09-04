package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionLimitsTest { @Test public void enoughCorrectionPasses(){assertTrue(PrecisionLimits.MAX_CORRECTION_PASSES>=20);} }
