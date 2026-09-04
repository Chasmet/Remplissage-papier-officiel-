package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionReadySignalTest { @Test public void loopSupported() throws Exception {assertTrue(PrecisionReadySignal.json("j").getBoolean("correction_loop_supported"));} }
