package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCorrectionReceiptTest { @Test public void exactResultReturned() throws Exception {TextOverlay o=new TextOverlay("x",0,.5f,.496f,"x",8f,"left","text",0,0,"known");assertEquals(.496,PrecisionCorrectionReceipt.json(o,2).getDouble("y"),.000001);} }
