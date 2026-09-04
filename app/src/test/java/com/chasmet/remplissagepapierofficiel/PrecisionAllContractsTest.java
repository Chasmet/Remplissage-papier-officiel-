package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionAllContractsTest { @Test public void allContractsPresent() throws Exception {assertNotNull(PrecisionAllContracts.json().getJSONObject("coordinates"));assertNotNull(PrecisionAllContracts.json().getJSONObject("signature"));} }
