package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCorrectionResponse { private PrecisionCorrectionResponse(){} public static JSONObject json(TextOverlay o,long revision) throws Exception {return new JSONObject().put("ok",true).put("overlay",OverlayJson.toJson(o)).put("preview",new JSONObject().put("revision",revision).put("full_page_pending",true).put("crop_pending",true)).put("final_pdf_pending_validation",true);} }
