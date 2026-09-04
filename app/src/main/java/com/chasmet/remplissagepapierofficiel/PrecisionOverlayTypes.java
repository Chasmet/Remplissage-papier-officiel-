package com.chasmet.remplissagepapierofficiel;
import org.json.JSONArray;
public final class PrecisionOverlayTypes { private PrecisionOverlayTypes(){} public static JSONArray json(){return new JSONArray().put(TextOverlay.KIND_TEXT).put(TextOverlay.KIND_CHECKBOX).put(TextOverlay.KIND_DATE).put(TextOverlay.KIND_SIGNATURE);} }
