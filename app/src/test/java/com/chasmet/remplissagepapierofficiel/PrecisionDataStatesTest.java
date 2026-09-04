package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionDataStatesTest { @Test public void userStateSupported(){assertTrue(PrecisionDataStates.json().toString().contains("requires_user"));} }
