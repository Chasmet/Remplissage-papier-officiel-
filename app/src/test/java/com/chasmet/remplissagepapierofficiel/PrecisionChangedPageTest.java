package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import java.util.ArrayList;import java.util.LinkedHashSet;import java.util.Set;
import static org.junit.Assert.*;
public class PrecisionChangedPageTest { @Test public void selectsModifiedPage(){ArrayList<TextOverlay>l=new ArrayList<>();l.add(new TextOverlay("a",0,.2f,.2f,"a",8f,"left","text",0,0,"known"));l.add(new TextOverlay("b",1,.2f,.2f,"b",8f,"left","text",0,0,"known"));Set<String>s=new LinkedHashSet<>();s.add("b");assertTrue(PrecisionChangedPage.from(l,s).contains(1));assertFalse(PrecisionChangedPage.from(l,s).contains(0));} }
