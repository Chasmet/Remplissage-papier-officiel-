package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionBuildInfoTest { @Test public void engineVersionPresent() throws Exception {assertEquals("1.10.0",PrecisionBuildInfo.json().getString("precision_engine"));} }
