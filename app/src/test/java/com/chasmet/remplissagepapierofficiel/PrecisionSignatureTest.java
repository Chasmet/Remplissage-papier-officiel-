package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionSignatureTest { @Test public void noAutoSignature() throws Exception {assertFalse(PrecisionSignature.json().getBoolean("chatgpt_auto_signature"));} }
