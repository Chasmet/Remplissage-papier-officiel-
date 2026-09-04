package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TextMeasurementTest {
    @Test public void returnsFontMetrics() throws Exception {
        JSONObject m=TextMeasurement.measure("skypieachannel@gmail.com",9f);
        assertTrue(m.getDouble("width")>0);
        assertTrue(m.getDouble("height")>0);
        assertEquals("exact",m.getString("baseline_reference"));
    }
}
