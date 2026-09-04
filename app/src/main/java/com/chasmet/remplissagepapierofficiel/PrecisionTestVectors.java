package com.chasmet.remplissagepapierofficiel;
import org.json.JSONArray;import org.json.JSONObject;
public final class PrecisionTestVectors { private PrecisionTestVectors(){} public static JSONArray json() throws Exception {return new JSONArray().put(new JSONObject().put("x",.605).put("y",.237)).put(new JSONObject().put("x",.245).put("y",.337)).put(new JSONObject().put("delta_y",-.004));} }
