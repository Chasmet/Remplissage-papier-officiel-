package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CorrectionResultTest {
    @Test public void correctionDoesNotRequireDocumentRebuild() throws Exception {
        JSONObject r=CorrectionResult.success("email_01");
        assertTrue(r.getBoolean("preview_required"));
        assertTrue(r.getBoolean("crop_required"));
        assertFalse(r.getBoolean("rebuild_document"));
    }
}
