package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionMcpAliases { private PrecisionMcpAliases(){} public static JSONObject json() throws Exception {return new JSONObject().put("get_active_document","paper_open_active_document").put("get_page_image","paper_get_page_image").put("render_preview","paper_get_preview_image").put("export_final_pdf","paper_get_filled_document");} }
