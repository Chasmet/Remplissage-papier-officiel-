package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayValidationWarningTest { @Test public void warningCannotMoveOverlay() throws Exception { assertTrue(OverlayValidationWarning.json("email_01","text_touches_line","warning").getBoolean("advisory")); } }
