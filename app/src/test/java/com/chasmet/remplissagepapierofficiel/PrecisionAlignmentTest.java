package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionAlignmentTest { @Test public void centerSupported(){assertTrue(PrecisionAlignment.json().toString().contains("center"));} }
