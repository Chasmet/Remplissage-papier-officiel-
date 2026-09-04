package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionDefaultsTest { @Test public void administrativeTextDefaultIsConservative(){assertTrue(PrecisionDefaults.DEFAULT_TEXT_SIZE<=9f);} }
