package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionMcpUpgradeTest { @Test public void noAuthPreserved() throws Exception {assertTrue(PrecisionMcpUpgrade.json().getBoolean("keep_no_auth_main_session"));} }
