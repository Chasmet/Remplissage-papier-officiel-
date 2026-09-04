package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class OverlayValidationState { private OverlayValidationState(){} public static JSONObject json(String id,boolean visuallyValidated,long revision) throws Exception {return new JSONObject().put("overlay_id",id==null?"":id).put("visually_validated",visuallyValidated).put("preview_revision",revision);} }
