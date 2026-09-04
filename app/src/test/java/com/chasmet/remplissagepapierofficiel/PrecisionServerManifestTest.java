package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionServerManifestTest { @Test public void noAuthSupported() throws Exception {assertTrue(PrecisionServerManifest.json().getBoolean("no_auth_supported"));assertTrue(PrecisionServerManifest.json().getJSONArray("new_tools").toString().contains("paper_update_overlay"));} }
