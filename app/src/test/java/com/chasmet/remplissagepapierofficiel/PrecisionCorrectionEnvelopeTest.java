package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionCorrectionEnvelopeTest { @Test public void staleRejected() throws Exception {assertTrue(PrecisionCorrectionEnvelope.json("j",2,new JSONObject()).getBoolean("reject_if_revision_changed"));} }
