package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionWorkStatusTest { @Test public void ciStillRequired() throws Exception {assertTrue(PrecisionWorkStatus.json().getString("ci").contains("requires"));} }
