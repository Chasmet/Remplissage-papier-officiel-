package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionBaseline { private PrecisionBaseline(){} public static JSONObject json() throws Exception {return new JSONObject().put("text_y_is_baseline",true).put("top_of_text_box",false).put("font_metrics_available",true).put("purpose","place text cleanly above administrative form lines");} }
