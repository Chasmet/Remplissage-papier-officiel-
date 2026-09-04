package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DateOverlayFactoryTest {
    @Test public void missingPartRequiresUser() {
        List<TextOverlay> p=DateOverlayFactory.split("date_01",0,"04","09","",.4f,.5f,.6f,.7f,8f);
        assertEquals(3,p.size());
        assertEquals(TextOverlay.STATE_REQUIRES_USER,p.get(2).dataState);
        assertEquals("",p.get(2).text);
    }
}
