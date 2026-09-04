package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionReviewOutcomeTest { @Test public void correctionRoutesToUpdate() throws Exception {assertEquals("update_overlay",PrecisionReviewOutcome.correction(2,"x").getString("next"));} }
