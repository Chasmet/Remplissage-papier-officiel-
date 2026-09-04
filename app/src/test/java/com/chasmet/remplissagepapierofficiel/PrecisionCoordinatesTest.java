package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCoordinatesTest { @Test public void noPercentHeuristic() throws Exception {assertFalse(PrecisionCoordinates.json().getBoolean("accept_percent_heuristic"));assertFalse(PrecisionCoordinates.json().getBoolean("clamp_invalid_values"));} }
