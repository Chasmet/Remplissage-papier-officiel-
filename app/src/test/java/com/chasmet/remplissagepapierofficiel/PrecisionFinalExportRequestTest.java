package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionFinalExportRequestTest { @Test public void exactRevisionRequired() throws Exception {assertTrue(PrecisionFinalExportRequest.json("j",4).getBoolean("require_exact_revision_match"));} }
