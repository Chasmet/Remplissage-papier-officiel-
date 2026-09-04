package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionPageRevisionTest { @Test public void cacheKeyChangesWithRevision() throws Exception {assertNotEquals(PrecisionPageRevision.json(0,1).getString("cache_key"),PrecisionPageRevision.json(0,2).getString("cache_key"));} }
