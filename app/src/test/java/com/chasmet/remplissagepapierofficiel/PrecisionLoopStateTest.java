package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrecisionLoopStateTest {
    @Test public void correctionInvalidatesOldValidation() {
        PrecisionLoopState s=new PrecisionLoopState();
        long r1=s.changed(); s.validate(r1); assertTrue(s.isCurrentValidated());
        s.changed(); assertFalse(s.isCurrentValidated());
    }
}
