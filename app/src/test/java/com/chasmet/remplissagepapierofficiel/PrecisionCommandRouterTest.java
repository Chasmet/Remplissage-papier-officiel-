package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PrecisionCommandRouterTest {
    @Test public void updatesSingleOverlay() throws Exception {
        List<TextOverlay> list=new ArrayList<>();
        list.add(new TextOverlay("email_01",0,.5f,.5f,"mail",8f,"left","text",0,0,"known"));
        JSONObject r=PrecisionCommandRouter.execute(new JSONObject().put("operation","update_overlay").put("overlay_id","email_01").put("y_delta",-.004),list);
        assertTrue(r.getBoolean("ok"));
        assertEquals(.496f,list.get(0).y,.00001f);
    }
}
