package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionChecklistTest { @Test public void approvalRequired(){assertTrue(PrecisionChecklist.json().toString().contains("approved before final export"));} }
