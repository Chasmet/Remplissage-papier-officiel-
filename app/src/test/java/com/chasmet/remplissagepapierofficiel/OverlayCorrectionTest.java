package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OverlayCorrectionTest {
    @Test public void correctionOnlyChangesTarget() throws Exception {
        List<TextOverlay> items = new ArrayList<>();
        items.add(new TextOverlay("identity_01",0,.2f,.3f,"CHEIKH",8f,"left","text",0,0,"known"));
        items.add(new TextOverlay("email_01",0,.4f,.5f,"mail@test.fr",8f,"left","text",0,0,"known"));
        JSONObject cmd = new JSONObject().put("overlay_id","email_01").put("y_delta",-.004);
        assertTrue(OverlayCorrection.apply(cmd, items));
        assertEquals(.3f, items.get(0).y, .00001f);
        assertEquals(.496f, items.get(1).y, .00001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesCorrectionOutsidePage() throws Exception {
        List<TextOverlay> items = new ArrayList<>();
        items.add(new TextOverlay("x",0,.5f,.001f,"X",8f,"left","text",0,0,"known"));
        OverlayCorrection.apply(new JSONObject().put("overlay_id","x").put("y_delta",-.01), items);
    }
}
