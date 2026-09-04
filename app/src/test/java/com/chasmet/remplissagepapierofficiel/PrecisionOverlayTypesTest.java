package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionOverlayTypesTest { @Test public void signatureSupported(){assertTrue(PrecisionOverlayTypes.json().toString().contains("signature"));} }
