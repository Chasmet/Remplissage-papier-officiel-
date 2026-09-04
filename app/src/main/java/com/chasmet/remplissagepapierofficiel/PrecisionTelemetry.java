package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
/** Contains geometry only; never document text or signature data. */
public final class PrecisionTelemetry { private PrecisionTelemetry(){} public static JSONObject correction(String overlayId,float dx,float dy,long revision) throws Exception {return new JSONObject().put("overlay_id",overlayId==null?"":overlayId).put("dx",dx).put("dy",dy).put("revision",revision).put("contains_document_text",false);} }
