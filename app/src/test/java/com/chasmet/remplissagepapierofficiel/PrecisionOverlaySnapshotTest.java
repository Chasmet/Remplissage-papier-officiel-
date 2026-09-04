package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;
public class PrecisionOverlaySnapshotTest { @Test public void idPreserved() throws Exception {ArrayList<TextOverlay>l=new ArrayList<>();l.add(new TextOverlay("email_01",0,.2f,.2f,"x",8f,"left","text",0,0,"known"));assertEquals("email_01",PrecisionOverlaySnapshot.json(l).getJSONObject(0).getString("overlay_id"));} }
