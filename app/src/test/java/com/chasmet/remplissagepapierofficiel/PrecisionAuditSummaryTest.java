package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionAuditSummaryTest { @Test public void releaseAfterCi() throws Exception {assertTrue(PrecisionAuditSummary.json().getBoolean("release_after_ci_only"));} }
