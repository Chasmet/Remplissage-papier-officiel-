package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionUiHintTest { @Test public void mentionsExactCoordinates(){assertTrue(PrecisionUiHint.text().contains("coordonnées exactes"));} }
