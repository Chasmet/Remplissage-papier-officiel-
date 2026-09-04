package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionBaselineTest { @Test public void baselineExact() throws Exception {assertTrue(PrecisionBaseline.json().getBoolean("text_y_is_baseline"));assertFalse(PrecisionBaseline.json().getBoolean("top_of_text_box"));} }
