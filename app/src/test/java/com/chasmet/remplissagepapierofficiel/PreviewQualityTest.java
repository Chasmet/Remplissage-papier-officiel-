package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewQualityTest { @Test public void previewIsHighResolution(){assertTrue(PreviewQuality.FULL_PAGE_MAX_DIMENSION>=1800);assertTrue(PreviewQuality.JPEG_QUALITY>=85);} }
