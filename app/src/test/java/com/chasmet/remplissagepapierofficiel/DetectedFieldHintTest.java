package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DetectedFieldHintTest {
    @Test public void cannotOverridePlacement() throws Exception {
        JSONObject h=DetectedFieldHint.create("Identité",1,.6f,.22f,.32f,.025f,"text_line",.9f);
        assertTrue(h.getBoolean("advisory_only"));
        assertFalse(h.getBoolean("may_modify_placement"));
    }
}
