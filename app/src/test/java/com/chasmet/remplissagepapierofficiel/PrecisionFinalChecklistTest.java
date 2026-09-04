package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionFinalChecklistTest { @Test public void versionCode26() throws Exception {assertEquals(26,PrecisionFinalChecklist.json().getInt("version_code"));} }
