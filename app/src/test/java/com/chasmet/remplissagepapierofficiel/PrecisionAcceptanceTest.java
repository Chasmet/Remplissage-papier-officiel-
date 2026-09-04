package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionAcceptanceTest {
 @Test public void criticalTrioEnabled() throws Exception { JSONObject a=PrecisionAcceptance.json(); assertTrue(a.getBoolean("strict_coordinates")); assertTrue(a.getBoolean("local_update")); assertTrue(a.getBoolean("full_preview")); }
}
