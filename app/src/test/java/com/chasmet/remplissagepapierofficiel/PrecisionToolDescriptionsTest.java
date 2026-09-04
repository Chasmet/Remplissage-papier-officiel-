package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionToolDescriptionsTest { @Test public void updateIsLocal() throws Exception {assertTrue(PrecisionToolDescriptions.json().getString("update_overlay").contains("never moves other overlays"));} }
