package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCorrectionReceipt { private PrecisionCorrectionReceipt(){} public static JSONObject json(TextOverlay o,long revision) throws Exception {return new JSONObject().put("overlay_id",o.overlayId).put("revision",revision).put("x",o.x).put("y",o.y).put("size",o.textSize).put("preview_required",true);} }
