package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionValidationResultTest { @Test public void correctionBlocksFinal() throws Exception {assertFalse(PrecisionValidationResult.correctionNeeded(2,"email_01").getBoolean("final_pdf_allowed"));} }
