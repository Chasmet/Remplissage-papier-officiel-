package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionFinalStateTest { @Test public void finalMatchesPreview() throws Exception {assertTrue(PrecisionFinalState.json("j",4).getBoolean("pdf_matches_validated_preview"));} }
