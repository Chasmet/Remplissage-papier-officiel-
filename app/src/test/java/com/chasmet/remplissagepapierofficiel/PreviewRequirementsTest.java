package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewRequirementsTest { @Test public void fullAndCropsRequired() throws Exception { assertTrue(PreviewRequirements.json().getBoolean("full_page")); assertTrue(PreviewRequirements.json().getBoolean("modified_overlay_crops")); } }
