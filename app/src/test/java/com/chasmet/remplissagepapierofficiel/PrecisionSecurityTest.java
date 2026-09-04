package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionSecurityTest { @Test public void chatGptCannotGenerateSignature() throws Exception {assertFalse(PrecisionSecurity.json().getBoolean("signature_generation_by_chatgpt"));} }
