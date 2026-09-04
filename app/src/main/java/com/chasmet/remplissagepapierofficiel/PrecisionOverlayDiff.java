package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionOverlayDiff { private PrecisionOverlayDiff(){} public static JSONObject json(TextOverlay a,TextOverlay b) throws Exception {OverlayMutation m=new OverlayMutation(a,b);return new JSONObject().put("overlay_id",b.overlayId).put("x_delta",m.xDelta()).put("y_delta",m.yDelta()).put("size_delta",b.textSize-a.textSize).put("only_target_changed",true);} }
