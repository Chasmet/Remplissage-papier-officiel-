package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionGuaranteesTest { @Test public void noClamp(){try{assertTrue(PrecisionGuarantees.json().getBoolean("no_coordinate_clamping"));}catch(Exception e){throw new RuntimeException(e);}} }
