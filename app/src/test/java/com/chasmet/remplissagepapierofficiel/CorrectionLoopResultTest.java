package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class CorrectionLoopResultTest { @Test public void awaitsVisualValidation() throws Exception {assertFalse(CorrectionLoopResult.applied(2,1).getBoolean("final"));assertTrue(CorrectionLoopResult.applied(2,1).getBoolean("chatgpt_validation_pending"));} }
