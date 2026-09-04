package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionServerActionMapTest { @Test public void cropAction() throws Exception {assertTrue(PrecisionServerActionMap.json().getString("preview_crop").contains("upload_preview_crop"));} }
