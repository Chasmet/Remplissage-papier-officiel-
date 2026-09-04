package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewValidationTokenTest { @Test public void tokenMustMatchCurrent() throws Exception {assertTrue(PreviewValidationToken.create("job",7).getBoolean("must_match_current_revision"));} }
