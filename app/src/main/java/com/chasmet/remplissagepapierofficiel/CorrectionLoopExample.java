package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class CorrectionLoopExample { private CorrectionLoopExample(){} public static JSONObject json() throws Exception {return new JSONObject().put("initial",new JSONObject().put("overlay_id","identity_01").put("x",.605).put("y",.237).put("size",8.0)).put("correction",new JSONObject().put("operation","update_overlay").put("overlay_id","identity_01").put("y_delta",-.0025)).put("next","render_preview + get_preview_crop + validate_layout");} }
