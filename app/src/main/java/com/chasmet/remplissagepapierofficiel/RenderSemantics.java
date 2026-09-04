package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class RenderSemantics { private RenderSemantics(){} public static JSONObject json() throws Exception { return new JSONObject().put("origin","top_left").put("x","normalized_anchor").put("text_y","normalized_baseline").put("checkbox_x_y","normalized_center").put("font_size","pdf_page_units").put("alignment","left_center_right").put("implicit_repositioning",false); } }
