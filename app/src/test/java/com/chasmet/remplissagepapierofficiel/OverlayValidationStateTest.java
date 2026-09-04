package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayValidationStateTest { @Test public void bindsValidationToRevision() throws Exception {assertEquals(4,OverlayValidationState.json("x",true,4).getLong("preview_revision"));} }
