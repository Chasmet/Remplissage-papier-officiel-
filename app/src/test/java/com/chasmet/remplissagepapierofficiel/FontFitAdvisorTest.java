package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class FontFitAdvisorTest { @Test public void advisoryOnly() throws Exception { assertFalse(FontFitAdvisor.advise("long text",12f,10f).getBoolean("applied")); } }
