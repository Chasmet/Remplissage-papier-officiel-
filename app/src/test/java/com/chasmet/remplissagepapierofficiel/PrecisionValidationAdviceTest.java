package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionValidationAdviceTest { @Test public void chatGptDecides() throws Exception {assertFalse(PrecisionValidationAdvice.json("x","text_touches_line").getBoolean("apply_correction_automatically"));assertEquals("ChatGPT",PrecisionValidationAdvice.json("x","p").getString("decision_owner"));} }
