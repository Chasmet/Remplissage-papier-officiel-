package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCropLabelsTest { @Test public void emailCrop(){TextOverlay o=new TextOverlay("email_01",0,.2f,.2f,"x",8f,"left","text",0,0,"known");assertEquals("crop_email_01",PrecisionCropLabels.label(o));} }
