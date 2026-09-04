package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OverlayQualityReportTest {
    @Test public void reportsUnknownAndLargeFont() throws Exception {
        List<TextOverlay> list = new ArrayList<>();
        list.add(new TextOverlay("x",0,.5f,.5f,"?",60f,"left","text",.1f,.02f,"unknown"));
        JSONObject report=OverlayQualityReport.build(list);
        assertFalse(report.getBoolean("ok"));
        assertTrue(report.getJSONArray("warnings").length()>=2);
    }
}
