package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionDateTest { @Test public void noInventedDate() throws Exception {assertFalse(PrecisionDate.json().getBoolean("invent_missing_component"));} }
