package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import static org.junit.Assert.*;

public class TextOverlayContractTest {
    @Test public void keepsCoordinatesExactly() {
        TextOverlay o = new TextOverlay("date_01",0,.605f,.237f,"04/09/2026",8.5f,"center","date",.2f,.03f,"known");
        assertEquals(.605f,o.x,0f);
        assertEquals(.237f,o.y,0f);
        assertEquals("date_01",o.overlayId);
        assertEquals(TextOverlay.KIND_DATE,o.kind);
    }

    @Test(expected = IllegalArgumentException.class)
    public void neverClampsInvalidCoordinates() {
        new TextOverlay("bad",0,1.01f,.2f,"x",8f,"left","text",0,0,"known");
    }
}
