package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PixelCorrectionTest { @Test public void convertsFourPixelsUp() throws Exception {assertEquals(-.0025,PixelCorrection.toNormalized("identity_01",0,-4,1000,1600).getDouble("y_delta"),.000001);} }
