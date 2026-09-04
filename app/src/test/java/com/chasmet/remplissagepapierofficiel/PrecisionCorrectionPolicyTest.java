package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCorrectionPolicyTest { @Test public void androidCannotMove(){assertFalse(PrecisionCorrectionPolicy.ANDROID_MAY_CHANGE_COORDINATES);assertTrue(PrecisionCorrectionPolicy.CHATGPT_DECIDES_POSITION);} }
