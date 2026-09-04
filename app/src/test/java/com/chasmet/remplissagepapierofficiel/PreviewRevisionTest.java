package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewRevisionTest {
    @Test public void identifiesFreshCorrectionPass() throws Exception {
        JSONObject r=PreviewRevision.create("job","cmd",3,"email_01");
        assertEquals(3,r.getLong("preview_revision"));
        assertEquals("email_01",r.getString("modified_overlay_id"));
        assertTrue(r.getBoolean("fresh"));
    }
}
