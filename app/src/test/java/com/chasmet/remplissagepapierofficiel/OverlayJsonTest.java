package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayJsonTest {
 @Test public void exposesBaseline() throws Exception { JSONObject j=OverlayJson.toJson(new TextOverlay("id",0,.2f,.3f,"x",8f,"left","text",0,0,"known")); assertEquals("baseline",j.getString("y_reference")); assertTrue(j.getBoolean("coordinates_authoritative")); }
}
