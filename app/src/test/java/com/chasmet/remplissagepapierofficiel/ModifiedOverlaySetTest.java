package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class ModifiedOverlaySetTest {
    @Test public void drainsOnlyChangedIds() {
        ModifiedOverlaySet s=new ModifiedOverlaySet();
        s.mark("email_01"); s.mark("email_01"); s.mark("date_01");
        Set<String> ids=s.drain();
        assertEquals(2,ids.size());
        assertTrue(s.isEmpty());
    }
}
