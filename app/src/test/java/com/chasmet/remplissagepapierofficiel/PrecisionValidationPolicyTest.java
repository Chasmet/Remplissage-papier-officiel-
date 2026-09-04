package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionValidationPolicyTest { @Test public void warningsCannotMove(){assertFalse(PrecisionValidationPolicy.WARNINGS_MAY_MOVE_OVERLAYS);assertTrue(PrecisionValidationPolicy.CHATGPT_COORDINATES_REMAIN_AUTHORITATIVE);} }
