package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class CorrectionLoopExampleTest { @Test public void correctionTargetsIdentityOnly() throws Exception {assertEquals("identity_01",CorrectionLoopExample.json().getJSONObject("correction").getString("overlay_id"));} }
