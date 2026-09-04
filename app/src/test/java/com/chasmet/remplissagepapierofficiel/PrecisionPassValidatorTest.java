package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import java.util.ArrayList;
public class PrecisionPassValidatorTest { @Test public void validPass(){ArrayList<TextOverlay>l=new ArrayList<>();l.add(new TextOverlay("x",0,.2f,.2f,"x",8f,"left","text",0,0,"known"));PrecisionPassValidator.validate(l);} }
