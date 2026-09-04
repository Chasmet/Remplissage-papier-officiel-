package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
public class PrecisionInvariantTest { @Test public void validOverlayPasses(){PrecisionInvariant.assertOverlay(new TextOverlay("x",0,.2f,.3f,"x",8f,"left","text",0,0,"known"));} }
