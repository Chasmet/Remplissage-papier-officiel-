package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionUserActions { private PrecisionUserActions(){} public static JSONObject forOverlay(TextOverlay o) throws Exception {String action="none";if(o!=null&&TextOverlay.STATE_REQUIRES_USER.equals(o.dataState))action="provide_value";if(o!=null&&TextOverlay.STATE_REQUIRES_SIGNATURE.equals(o.dataState))action="provide_signature_or_leave_blank";return new JSONObject().put("overlay_id",o==null?"":o.overlayId).put("action",action);} }
