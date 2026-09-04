package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionUserActionsTest { @Test public void signatureAction() throws Exception {TextOverlay o=new TextOverlay("s",0,.2f,.2f,"",8f,"left","signature",0,0,"requires_signature");assertTrue(PrecisionUserActions.forOverlay(o).getString("action").contains("signature"));} }
