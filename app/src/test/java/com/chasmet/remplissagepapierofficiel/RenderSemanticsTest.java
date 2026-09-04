package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class RenderSemanticsTest { @Test public void neverRepositions() throws Exception { assertFalse(RenderSemantics.json().getBoolean("implicit_repositioning")); } }
