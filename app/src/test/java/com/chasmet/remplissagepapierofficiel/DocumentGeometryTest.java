package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DocumentGeometryTest {
    @Test public void reportsSameNormalizedContract() throws Exception {
        JSONObject g=DocumentGeometry.page(0,595f,842f,1190,1684);
        assertEquals("top_left",g.getString("normalized_origin"));
        assertEquals("baseline",g.getString("text_y_reference"));
        assertEquals(595d,g.getDouble("pdf_width"),0d);
    }
}
