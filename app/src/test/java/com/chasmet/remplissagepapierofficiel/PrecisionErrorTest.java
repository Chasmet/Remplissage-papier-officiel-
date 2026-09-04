package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionErrorTest { @Test public void noSilentCoordinateFix() throws Exception { JSONObject e=PrecisionError.json("invalid_coordinate","x","bad"); assertFalse(e.getBoolean("coordinates_modified")); } }
