package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionServerRequirementsTest { @Test public void localUpdateRequired() throws Exception {assertTrue(PrecisionServerRequirements.json().getBoolean("accept_update_overlay"));} }
