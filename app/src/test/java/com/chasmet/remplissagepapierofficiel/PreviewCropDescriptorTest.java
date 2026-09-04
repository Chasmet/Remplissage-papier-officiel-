package com.chasmet.remplissagepapierofficiel;
import android.graphics.Rect;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewCropDescriptorTest {
 @Test public void linksCropToOverlay() throws Exception { TextOverlay o=new TextOverlay("email_01",0,.5f,.5f,"x",8f,"left","text",0,0,"known"); JSONObject j=PreviewCropDescriptor.json(o,new Rect(1,2,3,4),100,200); assertEquals("email_01",j.getString("overlay_id")); }
}
