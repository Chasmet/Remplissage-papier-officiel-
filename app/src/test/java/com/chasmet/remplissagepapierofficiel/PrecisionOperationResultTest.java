package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionOperationResultTest { @Test public void updateRequiresPreview() throws Exception {assertTrue(PrecisionOperationResult.ok("update_overlay",2).getBoolean("preview_required"));} }
