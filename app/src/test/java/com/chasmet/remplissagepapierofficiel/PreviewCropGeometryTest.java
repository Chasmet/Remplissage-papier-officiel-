package com.chasmet.remplissagepapierofficiel;

import android.graphics.Rect;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewCropGeometryTest {
    @Test public void cropStaysInsidePage() {
        TextOverlay o = new TextOverlay("email_01",0,.99f,.99f,"mail",8f,"left","text",.2f,.03f,"known");
        Rect r = PreviewCropGeometry.around(o,1000,1400,.02f,.01f);
        assertTrue(r.left >= 0 && r.top >= 0);
        assertTrue(r.right <= 1000 && r.bottom <= 1400);
        assertTrue(r.width() > 0 && r.height() > 0);
    }
}
