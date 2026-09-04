package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionPreviewContractTest { @Test public void sameCoordinates() throws Exception {assertTrue(PrecisionPreviewContract.json().getBoolean("same_normalized_coordinates_as_final"));} }
