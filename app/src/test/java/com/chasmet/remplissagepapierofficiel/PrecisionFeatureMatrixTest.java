package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionFeatureMatrixTest { @Test public void signatureUserOnly() throws Exception {assertEquals("user_only",PrecisionFeatureMatrix.json().getString("signature"));} }
