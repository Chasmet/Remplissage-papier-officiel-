package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class CorrectionRequestTest { @Test public void identifiesOverlay() throws Exception {assertEquals("email_01",CorrectionRequest.normalized("email_01",0,-.004f).getString("overlay_id"));} }
