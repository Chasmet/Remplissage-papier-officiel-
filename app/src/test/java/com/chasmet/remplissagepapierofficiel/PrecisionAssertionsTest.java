package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import java.util.ArrayList;
public class PrecisionAssertionsTest { @Test(expected=IllegalStateException.class) public void duplicateIdsRejected(){ArrayList<TextOverlay>l=new ArrayList<>();l.add(new TextOverlay("x",0,.2f,.2f,"a",8f,"left","text",0,0,"known"));l.add(new TextOverlay("x",0,.3f,.3f,"b",8f,"left","text",0,0,"known"));PrecisionAssertions.uniqueIds(l);} }
