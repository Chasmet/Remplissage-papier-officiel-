package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionReviewCycleTest { @Test public void continues() throws Exception {assertTrue(PrecisionReviewCycle.json(2,3).getBoolean("continue_until_validated"));} }
