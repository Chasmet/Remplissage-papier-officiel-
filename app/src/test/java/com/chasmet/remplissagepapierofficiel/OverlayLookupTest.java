package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OverlayLookupTest {
    @Test public void findsExactId(){ List<TextOverlay> l=new ArrayList<>(); l.add(new TextOverlay("email_01",0,.2f,.2f,"x",8f,"left","text",0,0,"known")); assertNotNull(OverlayLookup.byId(l,"email_01")); }
}
