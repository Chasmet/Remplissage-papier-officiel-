package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionPerformanceTest { @Test public void noGlobalOverlayRebuild(){assertFalse(PrecisionPerformance.REBUILD_ALL_OVERLAYS_ON_LOCAL_UPDATE);} }
