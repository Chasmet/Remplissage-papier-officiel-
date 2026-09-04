package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCoordinates { private PrecisionCoordinates(){} public static JSONObject json() throws Exception {return new JSONObject().put("range","0.000..1.000").put("origin","top-left").put("x_authoritative",true).put("y_authoritative",true).put("clamp_invalid_values",false).put("accept_percent_heuristic",false);} }
