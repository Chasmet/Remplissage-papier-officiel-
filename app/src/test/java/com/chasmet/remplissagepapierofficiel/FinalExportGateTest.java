package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class FinalExportGateTest {
    @Test public void requiresCurrentPreviewValidation() throws Exception {
        JSONObject g=FinalExportGate.evaluate(new ArrayList<>(),false);
        assertFalse(g.getBoolean("export_allowed"));
    }
}
