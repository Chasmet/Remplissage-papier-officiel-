package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayCorrectionExampleTest { @Test public void fourPxExampleIsUpdate() throws Exception {assertEquals("update_overlay",OverlayCorrectionExample.identityFourPixelsUp(1600).getString("operation"));} }
