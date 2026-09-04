package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionSummaryTest { @Test public void exposesCriticalTrio() throws Exception {assertTrue(PrecisionSummary.json().getString("critical_trio").contains("local_correction"));} }
