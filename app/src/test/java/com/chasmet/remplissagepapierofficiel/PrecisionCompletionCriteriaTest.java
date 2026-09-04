package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionCompletionCriteriaTest { @Test public void androidCannotChangeCoordinates() throws Exception {assertTrue(PrecisionCompletionCriteria.json().getBoolean("coordinates_unchanged_by_android"));} }
