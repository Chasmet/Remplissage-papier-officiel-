package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionPreviewEnvelopeTest { @Test public void currentStateFlag() throws Exception {assertTrue(PrecisionPreviewEnvelope.json("j",0,2,1000,1600).getBoolean("pixel_matches_current_overlay_state"));} }
