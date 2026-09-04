package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayCommandSchemaTest { @Test public void updateIsIncremental() throws Exception {assertFalse(OverlayCommandSchema.update().getBoolean("replaces_full_plan"));} }
