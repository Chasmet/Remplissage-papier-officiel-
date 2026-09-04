package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewCropPolicyTest { @Test public void cropVisible(){TextOverlay o=new TextOverlay("x",0,.5f,.5f,"x",8f,"left","text",0,0,"known");assertTrue(PreviewCropPolicy.forModified(o,1000,1600).width()>0);} }
