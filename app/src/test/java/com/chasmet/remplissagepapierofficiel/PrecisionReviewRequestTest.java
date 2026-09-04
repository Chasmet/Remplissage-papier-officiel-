package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionReviewRequestTest { @Test public void localCorrectionsOnly() throws Exception {assertTrue(PrecisionReviewRequest.json(2).getBoolean("respond_with_local_corrections_only"));} }
