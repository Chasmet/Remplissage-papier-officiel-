package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayDataStateTest {
    @Test public void preservesRequiresSignature() {
        TextOverlay o = new TextOverlay("signature_01",0,.5f,.5f,"",8f,"left","signature",.2f,.08f,"requires_signature");
        assertTrue(o.isSignature());
        assertEquals(TextOverlay.STATE_REQUIRES_SIGNATURE,o.dataState);
        assertEquals("",o.text);
    }

    @Test public void normalizesUnknown() {
        TextOverlay o = new TextOverlay("unknown_01",0,.2f,.2f,"",8f,"left","text",0,0,"UNKNOWN");
        assertEquals(TextOverlay.STATE_UNKNOWN,o.dataState);
    }
}
