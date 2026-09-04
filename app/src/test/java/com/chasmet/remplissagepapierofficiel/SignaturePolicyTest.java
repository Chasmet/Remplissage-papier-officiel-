package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import static org.junit.Assert.*;

public class SignaturePolicyTest {
    @Test public void refusesMissingUserSignature() {
        TextOverlay s=new TextOverlay("signature_01",0,.5f,.5f,"",8f,"left","signature",.2f,.08f,"requires_signature");
        assertFalse(SignaturePolicy.canRender(s,false));
        assertFalse(SignaturePolicy.canRender(s,true));
    }
}
