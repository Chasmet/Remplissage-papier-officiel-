package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayIdPolicyTest { @Test public void makesStableReadableId(){ assertEquals("email_01",OverlayIdPolicy.semantic("Email",1)); } }
