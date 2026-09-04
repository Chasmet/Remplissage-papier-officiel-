package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionAcceptanceReportTest { @Test public void containsServerRequirements() throws Exception {assertNotNull(PrecisionAcceptanceReport.json().getJSONObject("server_requirements"));} }
