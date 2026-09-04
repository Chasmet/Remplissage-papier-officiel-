package com.chasmet.remplissagepapierofficiel;
import org.json.JSONArray;
public final class PrecisionDataStates { private PrecisionDataStates(){} public static JSONArray json(){return new JSONArray().put(TextOverlay.STATE_KNOWN).put(TextOverlay.STATE_UNKNOWN).put(TextOverlay.STATE_REQUIRES_USER).put(TextOverlay.STATE_REQUIRES_SIGNATURE);} }
