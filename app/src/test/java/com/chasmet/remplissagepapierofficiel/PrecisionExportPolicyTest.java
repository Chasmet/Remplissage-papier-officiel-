package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionExportPolicyTest { @Test public void noSnapAtExport(){assertFalse(PrecisionExportPolicy.APPLY_AUTO_SNAP);assertTrue(PrecisionExportPolicy.USE_AUTHORITATIVE_OVERLAY_COORDINATES);} }
