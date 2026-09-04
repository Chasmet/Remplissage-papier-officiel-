package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrecisionLoopContractTest {
    @Test public void requiresPreviewAfterEveryChange() throws Exception { JSONObject c=PrecisionLoopContract.json(); assertTrue(c.getBoolean("preview_after_every_change")); assertFalse(c.getBoolean("full_plan_required_for_correction")); }
}
