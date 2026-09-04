package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionServerRequirements { private PrecisionServerRequirements(){} public static JSONObject json() throws Exception {return new JSONObject().put("accept_update_overlay",true).put("accept_delete_overlay",true).put("serve_document_geometry",true).put("serve_text_measurement",true).put("serve_preview_crop",true).put("serve_layout_validation",true).put("serve_final_pdf",true).put("token_optional",true);} }
