package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionCiExpectationTest { @Test public void assembleDebugRequired(){assertTrue(PrecisionCiExpectation.json().toString().contains("assembleDebug"));} }
