package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionOverlayDiffTest { @Test public void diffIsLocal() throws Exception {TextOverlay a=new TextOverlay("x",0,.5f,.5f,"x",8f,"left","text",0,0,"known");TextOverlay b=a.withPosition(.5f,.496f);assertTrue(PrecisionOverlayDiff.json(a,b).getBoolean("only_target_changed"));} }
