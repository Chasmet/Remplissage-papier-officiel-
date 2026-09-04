package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionFeatureSummaryTest { @Test public void localCorrectionsMentioned(){assertTrue(PrecisionFeatureSummary.text().contains("local delta corrections"));} }
