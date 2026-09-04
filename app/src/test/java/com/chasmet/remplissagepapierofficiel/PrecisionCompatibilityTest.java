package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCompatibilityTest { @Test public void legacyPreviewPreserved() throws Exception {assertTrue(PrecisionCompatibility.json().getBoolean("paper_get_preview_image"));} }
