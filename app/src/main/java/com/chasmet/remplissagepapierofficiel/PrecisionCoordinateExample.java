package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCoordinateExample { private PrecisionCoordinateExample(){} public static JSONObject json() throws Exception {float x=.605f,y=.237f,w=1000,h=1600;return new JSONObject().put("x",x).put("y",y).put("preview_x_px",CoordinateTransform.x(x,w)).put("preview_y_px",CoordinateTransform.y(y,h)).put("roundtrip_x",CoordinateTransform.normalizeX(CoordinateTransform.x(x,w),w)).put("roundtrip_y",CoordinateTransform.normalizeY(CoordinateTransform.y(y,h),h));} }
