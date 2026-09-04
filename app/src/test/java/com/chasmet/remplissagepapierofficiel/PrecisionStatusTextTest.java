package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionStatusTextTest { @Test public void correctionStatusClear(){assertTrue(PrecisionStatusText.correcting().contains("corrige"));} }
