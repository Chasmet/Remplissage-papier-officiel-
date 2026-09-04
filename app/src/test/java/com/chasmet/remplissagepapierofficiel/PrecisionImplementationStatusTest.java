package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionImplementationStatusTest { @Test public void honestStatus() throws Exception {assertEquals("pending_ci",PrecisionImplementationStatus.json().getString("release"));} }
