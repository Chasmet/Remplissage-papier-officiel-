package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OverlayOperationBatchTest {
    @Test public void appliesTwoLocalMoves() throws Exception {
        List<TextOverlay> l=new ArrayList<>(); l.add(new TextOverlay("x",0,.5f,.5f,"x",8f,"left","text",0,0,"known"));
        JSONArray a=new JSONArray().put(new JSONObject().put("operation","update_overlay").put("overlay_id","x").put("y_delta",-.01)).put(new JSONObject().put("operation","update_overlay").put("overlay_id","x").put("x_delta",.02));
        assertTrue(OverlayOperationBatch.apply(a,l).getBoolean("ok")); assertEquals(.52f,l.get(0).x,.00001f); assertEquals(.49f,l.get(0).y,.00001f);
    }
}
