package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCropRequestTest { @Test public void highResolution() throws Exception {assertTrue(PrecisionCropRequest.json("email_01",2).getBoolean("high_resolution"));} }
