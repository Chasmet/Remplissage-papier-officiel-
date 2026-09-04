package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionValidationContractTest { @Test public void warningsDoNotMove() throws Exception {assertTrue(PrecisionValidationContract.json().getBoolean("warnings_never_modify_coordinates"));} }
