package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionExpectedOutcomeTest { @Test public void pixelCorrection(){assertTrue(PrecisionExpectedOutcome.text().contains("few pixels"));} }
