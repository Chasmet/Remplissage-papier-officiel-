package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionTelemetryTest { @Test public void noDocumentText() throws Exception {assertFalse(PrecisionTelemetry.correction("x",0,-.004f,2).getBoolean("contains_document_text"));} }
