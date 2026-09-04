package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayCorrectionAuditTest {
 @Test public void recordsDeltaWithoutGlobalRewrite() throws Exception { TextOverlay a=new TextOverlay("x",0,.5f,.5f,"x",8f,"left","text",0,0,"known"); TextOverlay b=a.withPosition(.5f,.496f); JSONObject j=OverlayCorrectionAudit.record(a,b,2); assertTrue(j.getBoolean("other_overlays_unchanged")); assertEquals(.496,j.getDouble("after_y"),.0001); }
}
