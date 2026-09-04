package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionJobStateTest { @Test public void carriesEngineVersion() throws Exception {assertEquals("1.10.0",PrecisionJobState.json("j",1,"preview").getString("precision_engine"));} }
