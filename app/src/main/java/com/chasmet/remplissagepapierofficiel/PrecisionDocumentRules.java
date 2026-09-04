package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionDocumentRules { private PrecisionDocumentRules(){} public static JSONObject json() throws Exception {return new JSONObject().put("use_original_pdf_as_source",true).put("never_rasterize_final_pdf_unnecessarily",true).put("page_index_zero_based_in_protocol",true).put("normalized_coordinates_per_page",true).put("preserve_document_data_on_app_update",true);} }
