package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCoordinateExampleTest { @Test public void roundtrip() throws Exception {assertEquals(.605,PrecisionCoordinateExample.json().getDouble("roundtrip_x"),.000001);assertEquals(.237,PrecisionCoordinateExample.json().getDouble("roundtrip_y"),.000001);} }
