package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionDocumentSessionTest { @Test public void normalizedSession() throws Exception {assertEquals("normalized-0-to-1",PrecisionDocumentSession.json("j",2,1).getString("coordinate_system"));} }
