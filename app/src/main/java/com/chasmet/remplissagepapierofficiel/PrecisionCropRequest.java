package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCropRequest { private PrecisionCropRequest(){} public static JSONObject json(String overlayId,long revision) throws Exception {return new JSONObject().put("overlay_id",overlayId).put("preview_revision",revision).put("high_resolution",true).put("include_context_margin",true);} }
