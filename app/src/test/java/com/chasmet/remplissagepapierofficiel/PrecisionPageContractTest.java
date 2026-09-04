package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionPageContractTest { @Test public void sameTransform() throws Exception {assertTrue(PrecisionPageContract.json(0,1000,1600).getBoolean("same_transform_for_preview_and_final_pdf"));} }
