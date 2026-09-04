package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCommandExampleTest { @Test public void exampleIsLocalUpdate() throws Exception {assertEquals("update_overlay",PrecisionCommandExample.emailMoveUp().getString("operation"));} }
