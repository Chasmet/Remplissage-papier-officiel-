package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionServerResponseContractTest { @Test public void revisionReturned() throws Exception {assertTrue(PrecisionServerResponseContract.json().getBoolean("return_preview_revision"));} }
