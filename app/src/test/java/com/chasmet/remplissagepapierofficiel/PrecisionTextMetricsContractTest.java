package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionTextMetricsContractTest { @Test public void baselineMetric() throws Exception {assertTrue(PrecisionTextMetricsContract.json().getBoolean("baseline"));} }
