package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class CorrectionUnitsTest { @Test public void fourPixelsConvertsExactly(){assertEquals(-.0025f,CorrectionUnits.pixelsToNormalizedY(-4,1600),.000001f);} }
