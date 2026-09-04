package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionApiContractTest { @Test public void serverContractIncluded() throws Exception {assertNotNull(PrecisionApiContract.json().getJSONObject("server"));assertNotNull(PrecisionApiContract.json().getJSONObject("local_edits"));} }
