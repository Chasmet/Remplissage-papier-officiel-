package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class CorrectionPreviewPolicyTest { @Test public void updateAlwaysRenders(){assertTrue(CorrectionPreviewPolicy.shouldRenderAfter("update_overlay"));} }
