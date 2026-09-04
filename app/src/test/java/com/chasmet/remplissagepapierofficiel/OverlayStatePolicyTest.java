package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayStatePolicyTest { @Test public void signatureNeedsHuman(){TextOverlay o=new TextOverlay("s",0,.5f,.5f,"",8f,"left","signature",0,0,"requires_signature");assertTrue(OverlayStatePolicy.requiresHumanInput(o));} }
