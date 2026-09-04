package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
public class OverlayPlacementContractTest { @Test public void textPlacementValid(){OverlayPlacementContract.validate(new TextOverlay("id",0,.605f,.237f,"CHEIKH",8f,"left","text",0,0,"known"));} }
