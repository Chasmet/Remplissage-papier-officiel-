package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCorrectionResponseTest { @Test public void finalPending() throws Exception {TextOverlay o=new TextOverlay("x",0,.2f,.2f,"x",8f,"left","text",0,0,"known");assertTrue(PrecisionCorrectionResponse.json(o,2).getBoolean("final_pdf_pending_validation"));} }
