package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCommandExample { private PrecisionCommandExample(){} public static JSONObject emailMoveUp() throws Exception {return new JSONObject().put("operation","update_overlay").put("overlay_id","email_01").put("x_delta",0).put("y_delta",-.004).put("size",8.5);} }
