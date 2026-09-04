package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionMcpManifestTest { @Test public void exposesUpdateAndNoAuth() throws Exception {String s=PrecisionMcpManifest.json().getJSONArray("tools").toString();assertTrue(s.contains("update_overlay"));assertFalse(PrecisionMcpManifest.json().getBoolean("authentication_required"));} }
