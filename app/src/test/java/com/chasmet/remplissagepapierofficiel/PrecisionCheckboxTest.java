package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCheckboxTest { @Test public void exactCenter() throws Exception {assertEquals("exact_center",PrecisionCheckbox.json().getString("x_y_reference"));} }
