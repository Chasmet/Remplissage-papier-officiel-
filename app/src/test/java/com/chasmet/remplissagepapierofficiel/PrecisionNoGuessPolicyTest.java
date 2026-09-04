package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionNoGuessPolicyTest { @Test public void neverInventMissingValues() throws Exception {assertFalse(PrecisionNoGuessPolicy.json().getBoolean("invent_missing_values"));} }
