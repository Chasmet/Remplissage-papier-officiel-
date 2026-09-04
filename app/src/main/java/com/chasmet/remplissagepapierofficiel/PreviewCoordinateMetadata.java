package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PreviewCoordinateMetadata { private PreviewCoordinateMetadata(){} public static JSONObject json(int width,int height) throws Exception {return new JSONObject().put("width_px",width).put("height_px",height).put("origin","top_left").put("normalized_x_per_pixel",1d/width).put("normalized_y_per_pixel",1d/height).put("text_y_reference","baseline");} }
