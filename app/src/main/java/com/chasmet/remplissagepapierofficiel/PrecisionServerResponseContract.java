package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionServerResponseContract { private PrecisionServerResponseContract(){} public static JSONObject json() throws Exception {return new JSONObject().put("return_overlay_ids",true).put("return_preview_revision",true).put("return_page_geometry",true).put("return_validation_warnings",true).put("return_final_pdf_when_validated",true);} }
