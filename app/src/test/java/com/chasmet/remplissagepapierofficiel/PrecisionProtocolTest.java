package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrecisionProtocolTest {
    @Test public void advertisesAuthoritativeCorrectionLoop() throws Exception {
        JSONObject c=PrecisionProtocol.capabilities();
        assertTrue(c.getBoolean("coordinates_authoritative"));
        assertTrue(c.getBoolean("local_update"));
        assertTrue(c.getBoolean("preview_crops"));
        assertFalse(c.getBoolean("snapping"));
        assertEquals("baseline",c.getString("text_y_reference"));
    }
}
