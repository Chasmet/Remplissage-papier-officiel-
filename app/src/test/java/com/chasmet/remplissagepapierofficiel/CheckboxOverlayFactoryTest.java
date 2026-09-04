package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import static org.junit.Assert.*;

public class CheckboxOverlayFactoryTest {
    @Test public void preservesCenterExactly() {
        TextOverlay c=CheckboxOverlayFactory.create("box_01",0,.245f,.337f,true,"x",8f);
        assertEquals(.245f,c.x,0f);
        assertEquals(.337f,c.y,0f);
        assertEquals("center",c.align);
        assertEquals("X",c.text);
    }
}
