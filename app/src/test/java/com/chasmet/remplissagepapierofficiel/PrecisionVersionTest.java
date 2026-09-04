package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionVersionTest { @Test public void engineMatchesAppRelease(){ assertEquals("1.10.0",PrecisionVersion.ENGINE); } }
