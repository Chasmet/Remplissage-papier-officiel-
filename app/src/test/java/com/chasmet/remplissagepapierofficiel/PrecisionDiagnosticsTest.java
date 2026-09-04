package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionDiagnosticsTest { @Test public void diagnosticsExposeEngine() throws Exception { assertEquals("1.10.0",PrecisionDiagnostics.json().getString("engine")); } }
